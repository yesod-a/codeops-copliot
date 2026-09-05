[CmdletBinding()]
param([string]$HooksDirectory = '')

$ErrorActionPreference = 'Stop'
$modulePath = Join-Path $PSScriptRoot 'codeops-client.psm1'
Import-Module $modulePath -Force
if ([string]::IsNullOrWhiteSpace($HooksDirectory)) { $HooksDirectory = Join-Path (Get-CodeOpsClientDirectory) 'git-hooks' }
$expected = [IO.Path]::GetFullPath($HooksDirectory)
$currentResult = Invoke-CodeOpsGit -WorkingDirectory (Get-Location).Path -Arguments @('config', '--global', '--get', 'core.hooksPath') -AllowFailure
$current = if ($currentResult.ExitCode -eq 0 -and @($currentResult.Output).Count -gt 0) { (@($currentResult.Output)[0]).ToString().Trim() } else { '' }
if (-not $current -or -not [string]::Equals(([IO.Path]::GetFullPath($current)).TrimEnd('\'), $expected.TrimEnd('\'), [StringComparison]::OrdinalIgnoreCase)) {
    Write-Output 'CodeOps 全局 Git Hook 当前未启用，未执行卸载。'
    exit 0
}

$statePath = Get-CodeOpsHookStatePath
$previous = ''
if (Test-Path -LiteralPath $statePath) { $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json; $previous = [string]$state.previousHooksPath }
if ($previous) { & git config --global core.hooksPath $previous } else { & git config --global --unset core.hooksPath }
if ($LASTEXITCODE -ne 0 -and $previous) { throw '无法恢复原有全局 Git Hook 路径。' }
Write-Output "已卸载 CodeOps 全局 Git Hook：$expected"
if ($previous) { Write-Output "已恢复原有 Hook 路径：$previous" } else { Write-Output '已清除 CodeOps 的全局 core.hooksPath。' }
