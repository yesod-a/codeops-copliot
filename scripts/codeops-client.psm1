Set-StrictMode -Version Latest

function Get-CodeOpsClientDirectory {
    $base = if ([string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        Join-Path $env:USERPROFILE 'AppData\Local'
    } else { $env:LOCALAPPDATA }
    return (Join-Path $base 'CodeOps')
}

function Get-CodeOpsSettingsPath { return (Join-Path (Get-CodeOpsClientDirectory) 'settings.json') }
function Get-CodeOpsCredentialsPath { return (Join-Path (Get-CodeOpsClientDirectory) 'credentials.dat') }
function Get-CodeOpsHookStatePath { return (Join-Path (Get-CodeOpsClientDirectory) 'hook-state.json') }

function Set-CodeOpsUtf8Output {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [Console]::OutputEncoding = $encoding
    $global:OutputEncoding = $encoding
}

function Save-CodeOpsClientSettings {
    param([Parameter(Mandatory = $true)][hashtable]$Settings)
    $directory = Get-CodeOpsClientDirectory
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $path = Get-CodeOpsSettingsPath
    $temporary = "$path.tmp-$([guid]::NewGuid().ToString('N'))"
    try {
        [IO.File]::WriteAllText($temporary, ($Settings | ConvertTo-Json -Depth 8), (New-Object Text.UTF8Encoding($false)))
        Move-Item -LiteralPath $temporary -Destination $path -Force
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Get-CodeOpsClientSettings {
    $path = Get-CodeOpsSettingsPath
    if (-not (Test-Path -LiteralPath $path)) { throw 'CodeOps 尚未初始化，请先运行 initialize-codeops-client.ps1。' }
    return (Get-Content -Raw -LiteralPath $path | ConvertFrom-Json)
}

function Save-CodeOpsAgentToken {
    param([Parameter(Mandatory = $true)][string]$Token)
    if ([string]::IsNullOrWhiteSpace($Token)) { throw 'AgentToken 不能为空。' }
    $directory = Get-CodeOpsClientDirectory
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $secure = ConvertTo-SecureString -String $Token.Trim() -AsPlainText -Force
    $encrypted = ConvertFrom-SecureString -SecureString $secure
    [IO.File]::WriteAllText((Get-CodeOpsCredentialsPath), $encrypted, (New-Object Text.UTF8Encoding($false)))
}

function Get-CodeOpsAgentToken {
    $path = Get-CodeOpsCredentialsPath
    if (-not (Test-Path -LiteralPath $path)) { throw 'CodeOps Agent Token 不存在，请重新运行初始化脚本。' }
    $secure = ConvertTo-SecureString -String (Get-Content -Raw -LiteralPath $path).Trim()
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

function Invoke-CodeOpsGit {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& git -C $WorkingDirectory @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        $message = ($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
        throw "Git 命令失败：$message"
    }
    return [pscustomobject]@{ Output = $output; ExitCode = $exitCode }
}

function Get-CodeOpsRepositoryRoot {
    param([string]$WorkingDirectory = (Get-Location).Path)
    $result = Invoke-CodeOpsGit -WorkingDirectory $WorkingDirectory -Arguments @('rev-parse', '--show-toplevel')
    $root = @($result.Output) | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace([string]$root)) { throw '当前目录不在 Git 仓库中。' }
    return $root.ToString().Trim()
}

function Get-CodeOpsRemoteUrl {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot, [string]$RemoteName = 'origin')
    $result = Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('remote', 'get-url', $RemoteName)
    $remote = @($result.Output) | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace([string]$remote)) { throw "Git 远程仓库 '$RemoteName' 未配置。" }
    return $remote.ToString().Trim()
}

function ConvertFrom-CodeOpsJsonBytes {
    param([Parameter(Mandatory = $true)][byte[]]$Bytes)
    if ($Bytes.Length -eq 0) { return $null }
    return [Text.Encoding]::UTF8.GetString($Bytes) | ConvertFrom-Json
}

function Invoke-CodeOpsJsonPost {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)]$Body,
        [Parameter(Mandatory = $true)][string]$Token,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 12 -Compress }
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -Method Post `
        -Headers @{ Authorization = "Bearer $Token" } `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes($json)) -TimeoutSec $TimeoutSeconds
    return ConvertFrom-CodeOpsJsonBytes -Bytes $response.RawContentStream.ToArray()
}

function New-CodeOpsProjectResolutionPayload {
    param([Parameter(Mandatory = $true)][string]$RemoteUrl)
    return [pscustomobject]@{ remoteUrl = $RemoteUrl }
}

function New-CodeOpsReviewPayload {
    param(
        [Parameter(Mandatory = $true)][long]$ProjectId,
        [Parameter(Mandatory = $true)][string]$RepositoryKey,
        [Parameter(Mandatory = $true)][string]$Trigger,
        [string]$Branch,
        [string]$HeadCommit,
        [string]$BaseRef,
        [Parameter(Mandatory = $true)][object[]]$Files
    )
    return [pscustomobject]@{
        projectId = $ProjectId
        repositoryKey = $RepositoryKey
        title = "CodeOps $Trigger review"
        trigger = $Trigger
        branch = $Branch
        headCommit = $HeadCommit
        baseRef = $BaseRef
        files = @($Files | ForEach-Object {
            @{ path = $_.Path; gitStatus = $_.GitStatus; additions = [int]$_.Additions; deletions = [int]$_.Deletions; patch = [string]$_.Patch; contentHash = $null }
        })
    }
}

function Resolve-CodeOpsResolutionAction {
    param([Parameter(Mandatory = $true)]$Response)
    if ($null -eq $Response.projectId) { return 'SKIP' }
    if (-not [bool]$Response.reviewEnabled) { return 'SKIP' }
    if ([string]($Response.role) -eq 'VIEWER') { throw '当前用户没有该项目的代码评审权限。' }
    return 'REVIEW'
}

function ConvertFrom-CodeOpsPrePushLine {
    param([Parameter(Mandatory = $true)][string]$Line)
    $parts = $Line.Trim() -split '\s+'
    if ($parts.Count -ne 4) { throw "无效的 pre-push 更新记录：$Line" }
    return [pscustomobject]@{ LocalRef = $parts[0]; NewSha = $parts[1]; RemoteRef = $parts[2]; OldSha = $parts[3]; IsDeletion = $parts[1] -eq ('0' * 40) }
}

function Test-CodeOpsGitRef {
    param([Parameter(Mandatory = $true)][string]$WorkingDirectory, [Parameter(Mandatory = $true)][string]$Reference)
    $result = Invoke-CodeOpsGit -WorkingDirectory $WorkingDirectory -Arguments @('rev-parse', '--verify', '--quiet', "$Reference^{commit}") -AllowFailure
    return $result.ExitCode -eq 0
}

function Resolve-CodeOpsDiffBase {
    param([Parameter(Mandatory = $true)]$Update, [Parameter(Mandatory = $true)][string]$RepositoryRoot, [string]$RemoteName = 'origin')
    if ($Update.OldSha -ne ('0' * 40)) { return $Update.OldSha }
    foreach ($candidate in @("refs/remotes/$RemoteName/main", "refs/remotes/$RemoteName/master", 'refs/heads/main', 'refs/heads/master')) {
        if (Test-CodeOpsGitRef -WorkingDirectory $RepositoryRoot -Reference $candidate) {
            $result = Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('merge-base', $candidate, $Update.NewSha) -AllowFailure
            if ($result.ExitCode -eq 0 -and @($result.Output).Count -gt 0) { return (@($result.Output)[0]).ToString().Trim() }
        }
    }
    $parent = Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments @('rev-parse', '--verify', '--quiet', "$($Update.NewSha)^") -AllowFailure
    if ($parent.ExitCode -eq 0 -and @($parent.Output).Count -gt 0) { return (@($parent.Output)[0]).ToString().Trim() }
    return $null
}

function Get-CodeOpsChangedFiles {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot, [string]$BaseSha, [string]$HeadSha, [switch]$Staged)
    $rangeArgs = if ($Staged) { @('diff', '--cached') } elseif ($BaseSha -and $HeadSha) { @('diff', '--no-renames', '--no-ext-diff', $BaseSha, $HeadSha) } else { @('diff', 'HEAD^', 'HEAD') }
    $status = Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments ($rangeArgs + @('--name-status', '--'))
    $files = @()
    foreach ($item in @($status.Output)) {
        $line = $item.ToString(); if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $tab = $line.IndexOf("`t"); if ($tab -lt 1) { continue }
        $gitStatus = $line.Substring(0, $tab).Trim(); $path = $line.Substring($tab + 1).Trim()
        $prefix = if ($Staged) { @('diff', '--cached') } elseif ($BaseSha -and $HeadSha) { @('diff', '--no-renames', '--no-ext-diff', $BaseSha, $HeadSha) } else { @('diff', 'HEAD^', 'HEAD') }
        $numstat = Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments ($prefix + @('--numstat', '--', $path))
        $additions = 0; $deletions = 0
        if (@($numstat.Output).Count -gt 0) { $stats = (@($numstat.Output)[0]).ToString() -split "`t"; if ($stats.Count -ge 2) { [int]::TryParse($stats[0], [ref]$additions) | Out-Null; [int]::TryParse($stats[1], [ref]$deletions) | Out-Null } }
        $patchResult = Invoke-CodeOpsGit -WorkingDirectory $RepositoryRoot -Arguments ($prefix + @('--binary', '--unified=80', '--', $path))
        $files += [pscustomobject]@{ Path = $path.Replace('\', '/'); GitStatus = $gitStatus; Additions = $additions; Deletions = $deletions; Patch = (@($patchResult.Output) | ForEach-Object { $_.ToString() }) -join "`n" }
    }
    return $files
}

function Test-CodeOpsReviewablePath {
    param([Parameter(Mandatory = $true)][string]$Path)
    return $Path -match '\.(java|kt|kts|py|js|jsx|ts|tsx|vue|go|rs|c|cc|cpp|h|hpp|cs|rb|php|sql|xml|yml|yaml|json|properties|gradle|md)$'
}

Export-ModuleMember -Function *-CodeOps*
