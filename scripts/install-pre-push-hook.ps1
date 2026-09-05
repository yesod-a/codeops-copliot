[CmdletBinding()]
param(
    [string]$CodeOpsRoot = '',
    [string]$HooksDirectory = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$modulePath = Join-Path $PSScriptRoot 'codeops-client.psm1'
Import-Module $modulePath -Force

function Test-CodeOpsManagedHookDirectory {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path) -or -not (Test-Path -LiteralPath $Path)) { return $false }
    foreach ($hookName in @('pre-commit', 'pre-push', 'post-merge')) {
        $hookPath = Join-Path $Path $hookName
        if ((Test-Path -LiteralPath $hookPath) -and ((Get-Content -Raw -LiteralPath $hookPath) -match 'git-review-hook\.ps1')) {
            return $true
        }
    }
    return $false
}

if ([string]::IsNullOrWhiteSpace($CodeOpsRoot)) { $CodeOpsRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path }
$resolvedRoot = (Resolve-Path -LiteralPath $CodeOpsRoot).Path
if (-not (Test-Path -LiteralPath (Join-Path $resolvedRoot 'scripts\git-review-hook.ps1'))) { throw "CodeOps Hook 脚本不存在：$resolvedRoot" }
if ([string]::IsNullOrWhiteSpace($HooksDirectory)) { $HooksDirectory = Join-Path (Get-CodeOpsClientDirectory) 'git-hooks' }
$resolvedHooks = [IO.Path]::GetFullPath($HooksDirectory)
New-Item -ItemType Directory -Path $resolvedHooks -Force | Out-Null

$oldResult = Invoke-CodeOpsGit -WorkingDirectory (Get-Location).Path -Arguments @('config', '--global', '--get', 'core.hooksPath') -AllowFailure
$oldPath = if ($oldResult.ExitCode -eq 0 -and @($oldResult.Output).Count -gt 0) { (@($oldResult.Output)[0]).ToString().Trim() } else { '' }
$oldFullPath = if ([string]::IsNullOrWhiteSpace($oldPath)) { '' } else { [IO.Path]::GetFullPath($oldPath) }
$sameManagedPath = $oldFullPath -and ([string]::Equals($oldFullPath.TrimEnd('\'), $resolvedHooks.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase))
if ($sameManagedPath -and -not $Force) {
    Write-Output "CodeOps 全局 Git Hook 已安装：$resolvedHooks"
    exit 0
}
if ($oldPath -and -not $sameManagedPath -and -not $Force) { Write-Output "检测到已有全局 Hook 路径，将保留并在 CodeOps Hook 中链式调用：$oldPath" }

$existingState = $null
$statePath = Get-CodeOpsHookStatePath
if ($sameManagedPath -and (Test-Path -LiteralPath $statePath)) {
    try { $existingState = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json } catch { $existingState = $null }
}
$previousHooksPath = if ($sameManagedPath -and $existingState) { [string]$existingState.previousHooksPath } elseif ($sameManagedPath) { '' } else { $oldPath }
if (-not [string]::IsNullOrWhiteSpace($previousHooksPath)) {
    try {
        $previousFullPath = [IO.Path]::GetFullPath($previousHooksPath)
        if ([string]::Equals($previousFullPath.TrimEnd('\'), $resolvedHooks.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) {
            $previousHooksPath = ''
        }
    } catch {
        $previousHooksPath = ''
    }
}
if (Test-CodeOpsManagedHookDirectory -Path $previousHooksPath) { $previousHooksPath = '' }
$state = [ordered]@{ hooksDirectory = $resolvedHooks; previousHooksPath = $previousHooksPath; installedAt = [DateTime]::UtcNow.ToString('o'); codeOpsRoot = $resolvedRoot }
New-Item -ItemType Directory -Path (Get-CodeOpsClientDirectory) -Force | Out-Null
[IO.File]::WriteAllText((Get-CodeOpsHookStatePath), ($state | ConvertTo-Json -Depth 5), (New-Object Text.UTF8Encoding($false)))

$hookRoot = $resolvedRoot.Replace('\', '/')
$originalRoot = $previousHooksPath.Replace('\', '/')
$originalSnippet = if ($originalRoot) { "ORIGINAL_HOOKS_PATH='$originalRoot'" } else { "ORIGINAL_HOOKS_PATH=''" }
$hookDefinitions = @{
    'pre-push' = @"
#!/bin/sh
set -eu
$originalSnippet
PUSH_INPUT="`$(cat)"
if [ -n "`$ORIGINAL_HOOKS_PATH" ] && [ -x "`$ORIGINAL_HOOKS_PATH/pre-push" ]; then
  printf '%s\n' "`$PUSH_INPUT" | "`$ORIGINAL_HOOKS_PATH/pre-push" "`$@" || exit `$?
fi
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$hookRoot/scripts/git-review-hook.ps1" -Trigger pre-push -PushInput "`$PUSH_INPUT" -RemoteName "`$1"
"@
    'pre-commit' = @"
#!/bin/sh
set -eu
$originalSnippet
if [ -n "`$ORIGINAL_HOOKS_PATH" ] && [ -x "`$ORIGINAL_HOOKS_PATH/pre-commit" ]; then
  "`$ORIGINAL_HOOKS_PATH/pre-commit" "`$@"
fi
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$hookRoot/scripts/git-review-hook.ps1" -Trigger pre-commit "`$@"
"@
    'post-merge' = @"
#!/bin/sh
set -eu
$originalSnippet
if [ -n "`$ORIGINAL_HOOKS_PATH" ] && [ -x "`$ORIGINAL_HOOKS_PATH/post-merge" ]; then
  "`$ORIGINAL_HOOKS_PATH/post-merge" "`$@"
fi
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$hookRoot/scripts/git-review-hook.ps1" -Trigger post-merge "`$@"
"@
}
foreach ($entry in $hookDefinitions.GetEnumerator()) {
    $path = Join-Path $resolvedHooks $entry.Key
    if ((Test-Path -LiteralPath $path) -and -not $Force) { throw "CodeOps Hook 已存在，请使用 -Force 覆盖：$path" }
    [IO.File]::WriteAllText($path, $entry.Value.Trim() + "`n", (New-Object Text.UTF8Encoding($false)))
}

& git config --global core.hooksPath $resolvedHooks
if ($LASTEXITCODE -ne 0) { throw '无法配置全局 Git core.hooksPath。' }
Write-Output "已安装 CodeOps 全局 Git Hook：$resolvedHooks"
if ($oldPath -and -not $sameManagedPath) { Write-Output "已保留原有 Hook 路径并启用链式调用：$oldPath" }
