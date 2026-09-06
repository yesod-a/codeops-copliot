[CmdletBinding()]
param(
    [ValidateSet('pre-commit', 'pre-push', 'post-merge')][string]$Trigger = 'pre-push',
    [string]$PushInput = '',
    [string]$RemoteName = 'origin',
    [switch]$FailOpen,
    [switch]$NoExecute
)

$ErrorActionPreference = 'Stop'
$modulePath = Join-Path $PSScriptRoot 'codeops-client.psm1'
Import-Module $modulePath -Force
Set-CodeOpsUtf8Output

function Invoke-CodeOpsHookReview {
    param([Parameter(Mandatory = $true)]$Settings, [Parameter(Mandatory = $true)][string]$Token, [Parameter(Mandatory = $true)][string]$RepositoryRoot)

    $remoteUrl = Get-CodeOpsRemoteUrl -RepositoryRoot $RepositoryRoot -RemoteName $RemoteName
    Write-Host "[CodeOps] 正在解析远程仓库：$remoteUrl"
    $resolution = Invoke-CodeOpsJsonPost -Uri ($Settings.serverUrl.TrimEnd('/') + '/api/client/projects/resolve') `
        -Body (New-CodeOpsProjectResolutionPayload -RemoteUrl $remoteUrl) -Token $Token -TimeoutSeconds ([int]$Settings.timeoutSeconds)
    $script:CodeOpsResolvedFailOpen = [bool]$resolution.failOpen
    $action = Resolve-CodeOpsResolutionAction -Response $resolution
    if ($action -eq 'SKIP') {
        Write-Host '[CodeOps] 当前远程仓库未注册或项目 Hook 已禁用，跳过评审。'
        return $false
    }

    if ($Trigger -eq 'pre-push' -and -not [bool]$resolution.prePushEnabled) { Write-Host '[CodeOps] 项目未启用 pre-push 评审。'; return $false }
    if ($Trigger -eq 'pre-commit' -and -not [bool]$resolution.preCommitEnabled) { Write-Host '[CodeOps] 项目未启用 pre-commit 评审。'; return $false }
    if ($Trigger -eq 'post-merge' -and -not [bool]$resolution.postMergeEnabled) { Write-Host '[CodeOps] 项目未启用 post-merge 评审。'; return $false }

    $updates = @()
    if ($Trigger -eq 'pre-push') { $updates = @($PushInput -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { ConvertFrom-CodeOpsPrePushLine $_ }) }
    $allFiles = @(); $branch = ''; $headCommit = ''; $baseRef = $null
    if ($Trigger -eq 'pre-commit') {
        $allFiles = @(Get-CodeOpsChangedFiles -RepositoryRoot $RepositoryRoot -Staged)
        $branch = (@(Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('branch', '--show-current')).Output | Select-Object -First 1).ToString().Trim()
        $headCommit = (@(Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('rev-parse', 'HEAD')).Output | Select-Object -First 1).ToString().Trim()
    } elseif ($Trigger -eq 'post-merge') {
        $allFiles = @(Get-CodeOpsChangedFiles -RepositoryRoot $RepositoryRoot)
        $branch = (@(Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('branch', '--show-current')).Output | Select-Object -First 1).ToString().Trim()
        $headCommit = (@(Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('rev-parse', 'HEAD')).Output | Select-Object -First 1).ToString().Trim()
    } else {
        foreach ($update in $updates) {
            if ($update.IsDeletion) { continue }
            $base = Resolve-CodeOpsDiffBase -Update $update -RepositoryRoot $RepositoryRoot -RemoteName $RemoteName
            $allFiles += @(Get-CodeOpsChangedFiles -RepositoryRoot $RepositoryRoot -BaseSha $base -HeadSha $update.NewSha)
            if ([string]::IsNullOrWhiteSpace($branch)) { $branch = ($update.LocalRef -replace '^refs/heads/', '') }
            if ([string]::IsNullOrWhiteSpace($headCommit)) { $headCommit = $update.NewSha }
            if ([string]::IsNullOrWhiteSpace($baseRef)) { $baseRef = $base }
        }
    }

    $reviewFiles = @($allFiles | Where-Object { (Test-CodeOpsReviewablePath $_.Path) -and -not [string]::IsNullOrWhiteSpace($_.Patch) } |
        Group-Object -Property Path | ForEach-Object { $_.Group | Select-Object -First 1 })
    if ($reviewFiles.Count -eq 0) { Write-Host '[CodeOps] 没有可评审的文本变更，允许 Git 操作。'; return $false }

    $payload = New-CodeOpsReviewPayload -ProjectId ([long]$resolution.projectId) -RepositoryKey ([string]$resolution.repositoryKey) `
        -Trigger $Trigger -Branch $branch -HeadCommit $headCommit -BaseRef $baseRef -Files $reviewFiles
    Write-Host "[CodeOps] 正在评审 $($reviewFiles.Count) 个变更文件。"
    try {
        $result = Invoke-CodeOpsJsonPost -Uri ($Settings.serverUrl.TrimEnd('/') + '/api/agent/review-tasks') `
            -Body $payload -Token $Token -TimeoutSeconds ([int]$Settings.timeoutSeconds)
        Write-Host "[CodeOps] 已创建异步评审任务：$($result.taskId)"
        Write-Host '[CodeOps] 推送已放行，可在 CodeOps 任务中心查看结果。'
    } catch {
        Write-Host "[CodeOps] 异步评审任务创建失败：$($_.Exception.Message)"
        Write-Host '[CodeOps] 异步模式下不阻断 Git 操作，请稍后检查服务状态。'
    }
    return $false
}

if (-not $NoExecute) {
    $settings = $null
    $script:CodeOpsResolvedFailOpen = $false
    $advisory = $Trigger -eq 'post-merge'
    try {
        $settings = Get-CodeOpsClientSettings
        $token = Get-CodeOpsAgentToken
        $repositoryRoot = Get-CodeOpsRepositoryRoot
        $blocked = Invoke-CodeOpsHookReview -Settings $settings -Token $token -RepositoryRoot $repositoryRoot
        if ($blocked -and -not $advisory) { exit 1 }
        exit 0
    } catch {
        Write-Host "[CodeOps] $Trigger 评审失败：$($_.Exception.Message)"
        $failOpenEnabled = $FailOpen -or [bool]$settings.failOpen -or [bool]$script:CodeOpsResolvedFailOpen
        if ($advisory -or $failOpenEnabled) { Write-Host '[CodeOps] 已启用 fail-open，允许 Git 操作。'; exit 0 }
        Write-Host '[CodeOps] 默认 fail-closed，已阻止 Git 操作。'
        exit 1
    }
}
