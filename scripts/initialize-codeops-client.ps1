[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ServerUrl,
    [Parameter(Mandatory = $true)][string]$AgentToken,
    [string]$CodeOpsRoot = '',
    [ValidateRange(1, 3600)][int]$TimeoutSeconds = 600,
    [switch]$FailOpen,
    [switch]$Force,
    [switch]$SkipHookInstall
)

$ErrorActionPreference = 'Stop'
$modulePath = Join-Path $PSScriptRoot 'codeops-client.psm1'
Import-Module $modulePath -Force

$normalizedUrl = $ServerUrl.Trim().TrimEnd('/')
$parsedUri = $null
if (-not [Uri]::TryCreate($normalizedUrl, [UriKind]::Absolute, [ref]$parsedUri) -or $parsedUri.Scheme -notin @('http', 'https')) {
    throw 'ServerUrl 必须是绝对的 http 或 https 地址。'
}
if ([string]::IsNullOrWhiteSpace($AgentToken)) { throw 'AgentToken 不能为空。' }

$existingSettingsPath = Get-CodeOpsSettingsPath
if ((Test-Path -LiteralPath $existingSettingsPath) -and -not $Force) {
    throw "CodeOps 已初始化，如需覆盖请使用 -Force：$existingSettingsPath"
}

$settings = [ordered]@{
    serverUrl = $normalizedUrl
    timeoutSeconds = $TimeoutSeconds
    failOpen = [bool]$FailOpen
    codeOpsRoot = if ([string]::IsNullOrWhiteSpace($CodeOpsRoot)) { (Resolve-Path (Join-Path $PSScriptRoot '..')).Path } else { (Resolve-Path -LiteralPath $CodeOpsRoot).Path }
    initializedAt = [DateTime]::UtcNow.ToString('o')
}
Save-CodeOpsClientSettings -Settings $settings
Save-CodeOpsAgentToken -Token $AgentToken

if (-not $SkipHookInstall) {
    $installer = Join-Path $PSScriptRoot 'install-pre-push-hook.ps1'
    $arguments = @{ CodeOpsRoot = $settings.codeOpsRoot; Force = $Force }
    & $installer @arguments
}

Write-Output "CodeOps 客户端已初始化：$([Environment]::GetEnvironmentVariable('LOCALAPPDATA'))\CodeOps"
Write-Output 'Token 已使用当前 Windows 用户 DPAPI 加密保存。'
if (-not $SkipHookInstall) { Write-Output '已安装全局 pre-commit、pre-push、post-merge Hook。' }
