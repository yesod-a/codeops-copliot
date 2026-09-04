[CmdletBinding()]
param(
    [ValidateSet('pre-commit', 'pre-push', 'post-merge')]
    [string]$Trigger = 'pre-push',
    [string]$PushInput = '',
    [string]$PolicyUrl = $(if ($env:CODEOPS_POLICY_URL) { $env:CODEOPS_POLICY_URL } else { 'http://127.0.0.1:8080/api/projects/policy/resolve' }),
    [string]$AiUrl = $(if ($env:CODEOPS_AI_URL) { $env:CODEOPS_AI_URL } else { 'http://127.0.0.1:8090/api/ai/review' }),
    [string]$HistoryUrl = $(if ($env:CODEOPS_HISTORY_URL) { $env:CODEOPS_HISTORY_URL } else { 'http://127.0.0.1:8080/api/reviews' }),
    [int]$TimeoutSeconds = $(if ($env:CODEOPS_REVIEW_TIMEOUT_SECONDS) { [int]$env:CODEOPS_REVIEW_TIMEOUT_SECONDS } else { 600 }),
    [string]$RemoteName = 'origin',
    [switch]$NoExecute
)

$prePushScript = Join-Path $PSScriptRoot 'pre-push-review.ps1'
$script:CodeOpsPolicyUrl = $PolicyUrl
$script:CodeOpsAiUrl = $AiUrl
$script:CodeOpsHistoryUrl = $HistoryUrl
$script:CodeOpsTimeoutSeconds = $TimeoutSeconds
$script:CodeOpsPushInput = $PushInput
$script:CodeOpsRemoteName = $RemoteName
$script:CodeOpsExecuteHook = -not [bool]$NoExecute
. $prePushScript -NoExecute
Set-CodeOpsUtf8Output

function Test-TriggerEnabled {
    param(
        [Parameter(Mandatory = $true)]$Policy,
        [Parameter(Mandatory = $true)][string]$Trigger
    )

    if (-not [bool]$Policy.enabled) { return $false }
    switch ($Trigger) {
        'pre-commit' { return [bool]$Policy.preCommitEnabled }
        'pre-push' { return [bool]$Policy.prePushEnabled }
        'post-merge' { return [bool]$Policy.postMergeEnabled }
    }
    return $false
}

function Get-ReviewPolicy {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $query = [uri]::EscapeDataString($RepositoryRoot)
    return Invoke-RestMethod -Uri ($script:CodeOpsPolicyUrl + '?repositoryPath=' + $query) -Method Get -TimeoutSec 5
}

function Invoke-CodeOpsJsonPost {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$Body,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $utf8Body = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Post -ContentType 'application/json; charset=utf-8' -Body $utf8Body -TimeoutSec $TimeoutSeconds
    return ConvertFrom-CodeOpsJsonBytes -Bytes $response.RawContentStream.ToArray()
}

function Get-DiffFilesForTrigger {
    param(
        [Parameter(Mandatory = $true)][string]$Trigger,
        [string]$BaseSha,
        [string]$NewSha
    )

    if ($Trigger -eq 'pre-commit') {
        $statusLines = @(Invoke-GitOutput @('diff', '--cached', '--name-status', '--no-renames', '--'))
        $patchArguments = { param($path) @('diff', '--cached', '--no-ext-diff', '--unified=80', '--', $path) }
        $statArguments = { param($path) @('diff', '--cached', '--numstat', '--no-renames', '--', $path) }
    } elseif ($BaseSha) {
        $statusLines = @(Invoke-GitOutput @('diff', '--name-status', '--no-renames', '--no-ext-diff', $BaseSha, $NewSha, '--'))
        $patchArguments = { param($path) @('diff', '--no-ext-diff', '--unified=80', $BaseSha, $NewSha, '--', $path) }
        $statArguments = { param($path) @('diff', '--numstat', '--no-renames', $BaseSha, $NewSha, '--', $path) }
    } else {
        $statusLines = @(Invoke-GitOutput @('diff', '--name-status', '--no-renames', '--no-ext-diff', "$NewSha^", $NewSha, '--'))
        $patchArguments = { param($path) @('diff', '--no-ext-diff', '--unified=80', "$NewSha^", $NewSha, '--', $path) }
        $statArguments = { param($path) @('diff', '--numstat', '--no-renames', "$NewSha^", $NewSha, '--', $path) }
    }

    $files = @()
    foreach ($lineObject in $statusLines) {
        $line = $lineObject.ToString()
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $tab = $line.IndexOf("`t")
        if ($tab -lt 1) { continue }
        $status = $line.Substring(0, $tab).Trim()
        $path = $line.Substring($tab + 1).Trim().Replace('\', '/')
        if (-not (Test-ReviewablePath $path)) { continue }
        $stats = @(Invoke-GitOutput (& $statArguments $path))
        $additions = 0
        $deletions = 0
        if ($stats.Count -gt 0) {
            $parts = $stats[0].ToString() -split "`t"
            if ($parts.Count -ge 2) {
                [int]::TryParse($parts[0], [ref]$additions) | Out-Null
                [int]::TryParse($parts[1], [ref]$deletions) | Out-Null
            }
        }
        $patch = (@(Invoke-GitOutput (& $patchArguments $path)) | ForEach-Object { $_.ToString() }) -join "`n"
        if ([string]::IsNullOrWhiteSpace($patch)) { continue }
        $files += [pscustomobject]@{ Path = $path; GitStatus = $status; Additions = $additions; Deletions = $deletions; Patch = $patch }
    }
    return $files
}

function Invoke-TriggerReview {
    param(
        [Parameter(Mandatory = $true)][object[]]$Files,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)]$Policy,
        [string]$Scope = 'WORKTREE',
        [string]$BaseRef,
        [string]$Branch,
        [string]$HeadCommit
    )

    $reviewFiles = @($Files | Where-Object { (Test-ReviewablePath $_.Path) -and -not [string]::IsNullOrWhiteSpace($_.Patch) } | Select-Object -First 100)
    if ($reviewFiles.Count -eq 0) {
        Write-Host "[CodeOps] $Trigger：没有可评审的代码变更，允许继续。"
        return $false
    }
    Write-Host "[CodeOps] $Trigger：正在评审 $($reviewFiles.Count) 个变更文件。"
    $aiPayload = New-ReviewPayload -Repository $RepositoryRoot -Title $Title -Files $reviewFiles
    Write-Host "[CodeOps] $Trigger：请求 LLM，包含 $($reviewFiles.Count) 个文件。"
    $aiResponse = Invoke-CodeOpsJsonPost -Uri $script:CodeOpsAiUrl -Body $aiPayload -TimeoutSeconds $script:CodeOpsTimeoutSeconds
    $findings = @($aiResponse.findings)
    $savePayload = @{
        requestId = [guid]::NewGuid().ToString()
        repositoryPath = $RepositoryRoot
        repository = $RepositoryRoot
        title = $Title
        sourceType = 'GIT'
        scope = $Scope
        baseRef = $BaseRef
        branch = $Branch
        headCommit = $HeadCommit
        modelName = $(if ($env:AI_MODEL) { $env:AI_MODEL } else { 'hook-llm' })
        files = @($reviewFiles | ForEach-Object { @{ path = $_.Path; gitStatus = $_.GitStatus; additions = $_.Additions; deletions = $_.Deletions; patch = $_.Patch.Substring(0, [Math]::Min($_.Patch.Length, 1000000)); contentHash = $null } })
        findings = @($findings | ForEach-Object { @{ file = $_.file; category = $_.category; severity = $_.severity; line = $_.line; start_line = $_.start_line; end_line = $_.end_line; message = $_.message; suggestion = $_.suggestion; evidence = $_.evidence; confidence = $_.confidence } })
    } | ConvertTo-Json -Depth 12 -Compress
    Invoke-CodeOpsJsonPost -Uri $script:CodeOpsHistoryUrl -Body $savePayload -TimeoutSeconds 20 | Out-Null

    $blocking = @(Get-BlockingFindings -Findings $findings -FailOnSeverity $(if ($Policy.failOnSeverity) { $Policy.failOnSeverity } else { 'HIGH' }))
    if ($blocking.Count -gt 0) {
        Write-Host "[CodeOps] $Trigger：发现 $($blocking.Count) 个达到阻断级别的问题。"
        foreach ($finding in $blocking) { Write-Host "  [$($finding.severity)] $($finding.file):$($finding.line) $($finding.message)" }
        return $true
    }
    Write-Host "[CodeOps] $Trigger：评审通过，允许继续。"
    return $false
}

if ($script:CodeOpsExecuteHook) {
    Write-Host '[CodeOps] pre-push Hook 已启动。'
    $isAdvisory = $Trigger -eq 'post-merge'
    try {
        $repositoryRoot = (@(Invoke-GitOutput @('rev-parse', '--show-toplevel')))[0].ToString().Trim()
        $policy = Get-ReviewPolicy -RepositoryRoot $repositoryRoot
        if (-not (Test-TriggerEnabled -Policy $policy -Trigger $Trigger)) {
            Write-Host "[CodeOps] 项目未启用 $Trigger 评审，跳过。"
            exit 0
        }

        if ($Trigger -eq 'pre-push') {
            $updates = @($script:CodeOpsPushInput -split "`r?`n" | Where-Object { $_.Trim() })
            Write-Host "[CodeOps] pre-push 收到推送记录 $($updates.Count) 条。"
            $blocked = $false
            foreach ($line in $updates) {
                $update = ConvertFrom-PrePushLine $line
                if ($update.IsDeletion) { continue }
                $baseSha = Resolve-DiffBase -Update $update -RemoteName $script:CodeOpsRemoteName
                $files = @(Get-ChangedFiles -NewSha $update.NewSha -BaseSha $baseSha)
                $blocked = (Invoke-TriggerReview -Files $files -RepositoryRoot $repositoryRoot -Title "pre-push 评审: $($update.LocalRef) -> $($update.RemoteRef)" -Policy $policy -Scope 'BASE_COMMIT' -BaseRef $baseSha -Branch ($update.LocalRef -replace '^refs/heads/', '') -HeadCommit $update.NewSha) -or $blocked
            }
            if ($blocked) { exit 1 }
            exit 0
        }

        $branch = (@(Invoke-GitOutput @('branch', '--show-current')))[0].ToString().Trim()
        $headCommit = (@(Invoke-GitOutput @('rev-parse', 'HEAD')))[0].ToString().Trim()
        if ($Trigger -eq 'pre-commit') {
            $files = @(Get-DiffFilesForTrigger -Trigger $Trigger)
            $blocked = Invoke-TriggerReview -Files $files -RepositoryRoot $repositoryRoot -Title "pre-commit 评审: $branch" -Policy $policy -Scope 'STAGED' -Branch $branch -HeadCommit $headCommit
        } else {
            $parent = @(Invoke-GitOutput @('rev-parse', '--verify', '--quiet', 'HEAD^'))[0].ToString().Trim()
            $files = @(Get-DiffFilesForTrigger -Trigger $Trigger -BaseSha $parent -NewSha $headCommit)
            $blocked = Invoke-TriggerReview -Files $files -RepositoryRoot $repositoryRoot -Title "post-merge 评审: $branch" -Policy $policy -Scope 'BASE_COMMIT' -BaseRef $parent -Branch $branch -HeadCommit $headCommit
        }
        if ($blocked -and -not $isAdvisory) { exit 1 }
        exit 0
    } catch {
        Write-Host "[CodeOps] $Trigger 评审失败：$($_.Exception.Message)"
        if ($isAdvisory -or $policy.failOpen) { exit 0 }
        Write-Host '[CodeOps] 默认 fail-closed，已阻止 Git 操作。'
        exit 1
    }
}
