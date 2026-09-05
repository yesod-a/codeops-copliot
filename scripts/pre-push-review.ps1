[CmdletBinding()]
param(
    [string]$ServerUrl = $(if ($env:CODEOPS_SERVER_URL) { $env:CODEOPS_SERVER_URL } else { '' }),
    [string]$AgentToken = $(if ($env:CODEOPS_AGENT_TOKEN) { $env:CODEOPS_AGENT_TOKEN } else { '' }),
    [long]$ProjectId = $(if ($env:CODEOPS_PROJECT_ID) { [long]$env:CODEOPS_PROJECT_ID } else { 0 }),
    [string]$AiUrl = $(if ($env:CODEOPS_AI_URL) { $env:CODEOPS_AI_URL } else { 'http://127.0.0.1:8090/api/ai/review' }),
    [string]$HistoryUrl = $(if ($env:CODEOPS_HISTORY_URL) { $env:CODEOPS_HISTORY_URL } else { 'http://127.0.0.1:8080/api/reviews' }),
    [ValidateSet('OFF', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')]
    [string]$FailOnSeverity = $(if ($env:CODEOPS_REVIEW_FAIL_ON_SEVERITY) { $env:CODEOPS_REVIEW_FAIL_ON_SEVERITY } else { 'HIGH' }),
    [int]$TimeoutSeconds = $(if ($env:CODEOPS_REVIEW_TIMEOUT_SECONDS) { [int]$env:CODEOPS_REVIEW_TIMEOUT_SECONDS } else { 600 }),
    [switch]$FailOpen,
    [string]$PushInput,
    [switch]$NoExecute
)

function Set-CodeOpsUtf8Output {
    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [Console]::OutputEncoding = $utf8NoBom
    $global:OutputEncoding = $utf8NoBom
}

Set-CodeOpsUtf8Output

$script:SeverityRank = @{
    LOW = 1
    MEDIUM = 2
    HIGH = 3
    CRITICAL = 4
}

function ConvertFrom-PrePushLine {
    param([Parameter(Mandatory = $true)][string]$Line)

    $parts = $Line.Trim() -split '\s+'
    if ($parts.Count -ne 4) {
        throw "Invalid pre-push update line: $Line"
    }

    [pscustomobject]@{
        LocalRef   = $parts[0]
        NewSha     = $parts[1]
        RemoteRef  = $parts[2]
        OldSha     = $parts[3]
        IsDeletion = $parts[1] -eq ('0' * 40)
    }
}

function Get-BlockingFindings {
    param(
        [Parameter(Mandatory = $true)][object[]]$Findings,
        [Parameter(Mandatory = $true)][string]$FailOnSeverity
    )

    if ($FailOnSeverity -eq 'OFF') {
        return @()
    }

    $threshold = $script:SeverityRank[$FailOnSeverity]
    return @($Findings | Where-Object {
        $severity = [string]$_.severity
        $script:SeverityRank.ContainsKey($severity) -and $script:SeverityRank[$severity] -ge $threshold
    })
}

function Invoke-GitOutput {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& git @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $message = ($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
        throw "Git command failed: $message"
    }
    return $output
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

function ConvertFrom-CodeOpsJsonBytes {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)

    if ($Bytes.Length -eq 0) { return $null }
    return [System.Text.Encoding]::UTF8.GetString($Bytes) | ConvertFrom-Json
}

function Test-GitRef {
    param([Parameter(Mandatory = $true)][string]$Reference)

    & git rev-parse --verify --quiet "$Reference^{commit}" *> $null
    return $LASTEXITCODE -eq 0
}

function Resolve-DiffBase {
    param(
        [Parameter(Mandatory = $true)]$Update,
        [Parameter(Mandatory = $true)][string]$RemoteName
    )

    if ($Update.OldSha -ne ('0' * 40)) {
        return $Update.OldSha
    }

    $candidates = @(
        "refs/remotes/$RemoteName/main",
        "refs/remotes/$RemoteName/master",
        'refs/heads/main',
        'refs/heads/master'
    )
    foreach ($candidate in $candidates) {
        if (Test-GitRef $candidate) {
            $mergeBase = @(& git merge-base $candidate $Update.NewSha 2>$null)
            if ($LASTEXITCODE -eq 0 -and $mergeBase.Count -gt 0) {
                return $mergeBase[0].ToString().Trim()
            }
        }
    }

    $parent = @(& git rev-parse --verify --quiet "$($Update.NewSha)^" 2>$null)
    if ($LASTEXITCODE -eq 0 -and $parent.Count -gt 0) {
        return $parent[0].ToString().Trim()
    }
    return $null
}

function Get-ChangedFiles {
    param(
        [Parameter(Mandatory = $true)][string]$NewSha,
        [string]$BaseSha
    )

    if ($BaseSha) {
        $statusLines = @(Invoke-GitOutput @('diff', '--name-status', '--no-renames', '--no-ext-diff', $BaseSha, $NewSha, '--'))
    } else {
        $statusLines = @(Invoke-GitOutput @('diff-tree', '--root', '--no-commit-id', '--name-status', '-r', $NewSha, '--'))
    }

    $files = @()
    foreach ($lineObject in $statusLines) {
        $line = $lineObject.ToString()
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $tab = $line.IndexOf("`t")
        if ($tab -lt 1) { continue }
        $status = $line.Substring(0, $tab).Trim()
        $path = $line.Substring($tab + 1).Trim()
        if ([string]::IsNullOrWhiteSpace($path)) { continue }

        if ($BaseSha) {
            $numstat = @(Invoke-GitOutput @('diff', '--numstat', '--no-renames', '--no-ext-diff', $BaseSha, $NewSha, '--', $path))
        } else {
            $numstat = @(Invoke-GitOutput @('diff-tree', '--root', '--no-commit-id', '--numstat', '-r', $NewSha, '--', $path))
        }
        $additions = 0
        $deletions = 0
        if ($numstat.Count -gt 0) {
            $stats = $numstat[0].ToString() -split "`t"
            if ($stats.Count -ge 2) {
                [int]::TryParse($stats[0], [ref]$additions) | Out-Null
                [int]::TryParse($stats[1], [ref]$deletions) | Out-Null
            }
        }

        if ($BaseSha) {
            $patchLines = @(Invoke-GitOutput @('diff', '--binary', '--unified=80', '--no-ext-diff', $BaseSha, $NewSha, '--', $path))
        } else {
            $patchLines = @(Invoke-GitOutput @('diff-tree', '--root', '--binary', '--unified=80', '-p', $NewSha, '--', $path))
        }
        $patch = ($patchLines | ForEach-Object { $_.ToString() }) -join "`n"
        $files += [pscustomobject]@{
            Path       = $path.Replace('\', '/')
            GitStatus  = $status
            Additions  = $additions
            Deletions  = $deletions
            Patch      = $patch
        }
    }
    return $files
}

function Test-ReviewablePath {
    param([Parameter(Mandatory = $true)][string]$Path)

    return $Path -match '\.(java|kt|kts|py|js|jsx|ts|tsx|vue|go|rs|c|cc|cpp|h|hpp|cs|rb|php|sql|xml|yml|yaml|json|properties|gradle|md)$'
}

function Get-ReviewFileCharLimit {
    $parsedValue = 0
    if ([int]::TryParse($env:CODEOPS_REVIEW_FILE_CHARS, [ref]$parsedValue) -and $parsedValue -gt 0) {
        return $parsedValue
    }
    return 30000
}

function New-ReviewPayload {
    param(
        [Parameter(Mandatory = $true)][string]$Repository,
        [Parameter(Mandatory = $true)][string]$Title,
        [Parameter(Mandatory = $true)][object[]]$Files,
        [int]$MaxCharsPerFile = $(Get-ReviewFileCharLimit)
    )

    $requestFiles = @($Files | ForEach-Object {
        $content = [string]$_.Patch
        if ([string]::IsNullOrWhiteSpace($content)) { return }
        $content = $content.Substring(0, [Math]::Min($content.Length, $MaxCharsPerFile))
        @{ path = $_.Path; content = $content }
    })
    return @{
        repository = $Repository
        title = $Title
        files = $requestFiles
    } | ConvertTo-Json -Depth 8 -Compress
}

function Invoke-PushReview {
    param(
        [Parameter(Mandatory = $true)]$Update,
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$RemoteName,
        [Parameter(Mandatory = $true)][string]$ReviewAiUrl,
        [Parameter(Mandatory = $true)][string]$ReviewHistoryUrl,
        [Parameter(Mandatory = $true)][string]$ReviewFailOnSeverity,
        [Parameter(Mandatory = $true)][int]$ReviewTimeoutSeconds
    )

    $baseSha = Resolve-DiffBase -Update $Update -RemoteName $RemoteName
    $changedFiles = @(Get-ChangedFiles -NewSha $Update.NewSha -BaseSha $baseSha)
    $reviewFiles = @($changedFiles | Where-Object { (Test-ReviewablePath $_.Path) -and -not [string]::IsNullOrWhiteSpace($_.Patch) } | Select-Object -First 100)
    if ($reviewFiles.Count -eq 0) {
        Write-Host "[CodeOps] $($Update.LocalRef): 没有可评审的文本代码变更，允许推送。"
        return $false
    }

    if ($ServerUrl -and $AgentToken -and $ProjectId -gt 0) {
        $endpoint = $ServerUrl.TrimEnd('/') + '/api/agent/reviews'
        $branch = ($Update.LocalRef -replace '^refs/heads/', '')
        $payload = @{
            projectId = $ProjectId
            repositoryKey = (Split-Path $RepositoryRoot -Leaf)
            title = "pre-push 评审: $branch -> $($Update.RemoteRef)"
            branch = $branch
            headCommit = $Update.NewSha
            baseRef = $baseSha
            files = @($reviewFiles | ForEach-Object { @{ path = $_.Path; gitStatus = $_.GitStatus; additions = $_.Additions; deletions = $_.Deletions; patch = $_.Patch; contentHash = $null } })
        } | ConvertTo-Json -Depth 10 -Compress
        Write-Host "[CodeOps] 请求中央评审服务，包含 $($reviewFiles.Count) 个文件。"
        $headers = @{ Authorization = "Bearer $AgentToken" }
        $response = Invoke-WebRequest -UseBasicParsing -Uri $endpoint -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($payload)) -TimeoutSec $ReviewTimeoutSeconds
        $central = ConvertFrom-CodeOpsJsonBytes -Bytes $response.RawContentStream.ToArray()
        $findings = @($central.review.findings)
        if ($central.blocked) {
            Write-Host "[CodeOps] 中央评审阻止推送：$($central.blockReason)"
            foreach ($finding in $findings) { Write-Host "  [$($finding.severity)] $($finding.file):$($finding.line) $($finding.message)" }
        } else {
            Write-Host '[CodeOps] 中央评审通过，允许推送。'
        }
        return [bool]$central.blocked
    }

    $branch = ($Update.LocalRef -replace '^refs/heads/', '')
    $title = "pre-push 评审: $branch -> $($Update.RemoteRef)"
    Write-Host "[CodeOps] 正在评审 $($reviewFiles.Count) 个变更文件 ($branch)。"
    $aiPayload = New-ReviewPayload -Repository $RepositoryRoot -Title $title -Files $reviewFiles
    Write-Host "[CodeOps] 请求 LLM，包含 $($reviewFiles.Count) 个文件。"
    $aiResponse = Invoke-CodeOpsJsonPost -Uri $ReviewAiUrl -Body $aiPayload -TimeoutSeconds $ReviewTimeoutSeconds
    $findings = @($aiResponse.findings)
    $blockingFindings = @(Get-BlockingFindings -Findings $findings -FailOnSeverity $ReviewFailOnSeverity)

    $savePayload = @{
        requestId      = [guid]::NewGuid().ToString()
        repositoryPath = $RepositoryRoot
        repository     = $RepositoryRoot
        title          = $title
        sourceType     = 'GIT'
        scope          = 'BASE_COMMIT'
        baseRef        = $baseSha
        branch         = $branch
        headCommit     = $Update.NewSha
        modelName      = $(if ($env:AI_MODEL) { $env:AI_MODEL } else { 'pre-push-llm' })
        files          = @($changedFiles | Select-Object -First 100 | ForEach-Object {
            @{
                path        = $_.Path
                gitStatus   = $_.GitStatus
                additions   = $_.Additions
                deletions   = $_.Deletions
                patch       = $_.Patch.Substring(0, [Math]::Min($_.Patch.Length, 1000000))
                contentHash = $null
            }
        })
        findings       = @($findings | ForEach-Object {
            @{
                file       = $_.file
                category   = $_.category
                severity   = $_.severity
                line       = $_.line
                start_line = $_.start_line
                end_line   = $_.end_line
                message    = $_.message
                suggestion = $_.suggestion
                evidence   = $_.evidence
                confidence = $_.confidence
            }
        })
    } | ConvertTo-Json -Depth 12 -Compress
    Invoke-CodeOpsJsonPost -Uri $ReviewHistoryUrl -Body $savePayload -TimeoutSeconds 20 | Out-Null

    if ($blockingFindings.Count -gt 0) {
        Write-Host "[CodeOps] 评审阻止推送：发现 $($blockingFindings.Count) 个 $ReviewFailOnSeverity 及以上问题。"
        foreach ($finding in $blockingFindings) {
            Write-Host "  [$($finding.severity)] $($finding.file):$($finding.line) $($finding.message)"
        }
        return $true
    }

    Write-Host "[CodeOps] 评审通过：没有达到 $ReviewFailOnSeverity 阈值的问题，允许推送。"
    return $false
}

if (-not $NoExecute) {
    $failOpenEnabled = $FailOpen -or $env:CODEOPS_REVIEW_FAIL_OPEN -eq 'true'
    try {
        $repositoryRoot = (Invoke-GitOutput @('rev-parse', '--show-toplevel'))[0].ToString().Trim()
        $remoteName = if ($args.Count -gt 0) { $args[0] } else { 'origin' }
        $updates = @($PushInput -split "`r?`n" | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
        $blocked = $false

        foreach ($line in $updates) {
            $update = ConvertFrom-PrePushLine $line
            if ($update.IsDeletion) {
                Write-Host "[CodeOps] 跳过删除远程引用：$($update.RemoteRef)"
                continue
            }
            try {
                $blocked = (Invoke-PushReview -Update $update -RepositoryRoot $repositoryRoot -RemoteName $remoteName -ReviewAiUrl $AiUrl -ReviewHistoryUrl $HistoryUrl -ReviewFailOnSeverity $FailOnSeverity -ReviewTimeoutSeconds $TimeoutSeconds) -or $blocked
            } catch {
                Write-Host "[CodeOps] 评审失败：$($_.Exception.Message)"
                if (-not $failOpenEnabled) {
                    $blocked = $true
                    Write-Host '[CodeOps] 默认 fail-closed，已阻止推送。可使用 -FailOpen 或设置 CODEOPS_REVIEW_FAIL_OPEN=true 放行。'
                } else {
                    Write-Host '[CodeOps] 已启用 fail-open，允许推送。'
                }
            }
        }
        if ($blocked) { exit 1 }
        exit 0
    } catch {
        Write-Host "[CodeOps] pre-push 初始化失败：$($_.Exception.Message)"
        if ($failOpenEnabled) { exit 0 }
        exit 1
    }
}
