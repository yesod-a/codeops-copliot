[CmdletBinding()]
param(
    [string]$CodeOpsRoot = '',
    [string]$HooksDirectory = (Join-Path $HOME '.codeops\git-hooks'),
    [switch]$Force
)

if ([string]::IsNullOrWhiteSpace($CodeOpsRoot)) {
    $CodeOpsRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

$resolvedRoot = (Resolve-Path -LiteralPath $CodeOpsRoot).Path
if (-not (Test-Path -LiteralPath (Join-Path $resolvedRoot 'scripts\git-review-hook.ps1'))) {
    throw "CodeOps hook script was not found under: $resolvedRoot"
}

New-Item -ItemType Directory -Path $HooksDirectory -Force | Out-Null
$hookRoot = $resolvedRoot.Replace('\', '/')
$hookDefinitions = @{
    'pre-push' = @"
#!/bin/sh
set -eu
PUSH_INPUT="`$(cat)"
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$hookRoot/scripts/git-review-hook.ps1" -Trigger pre-push -PushInput "`$PUSH_INPUT" "`$@"
"@
    'pre-commit' = @"
#!/bin/sh
set -eu
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$hookRoot/scripts/git-review-hook.ps1" -Trigger pre-commit "`$@"
"@
    'post-merge' = @"
#!/bin/sh
set -eu
exec powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$hookRoot/scripts/git-review-hook.ps1" -Trigger post-merge "`$@"
"@
}

foreach ($entry in $hookDefinitions.GetEnumerator()) {
    $hookPath = Join-Path $HooksDirectory $entry.Key
    if ((Test-Path -LiteralPath $hookPath) -and -not $Force) {
        throw "A global hook already exists. Re-run with -Force to replace it: $hookPath"
    }
    if (Test-Path -LiteralPath $hookPath) {
        Copy-Item -LiteralPath $hookPath -Destination "$hookPath.codeops-backup" -Force
    }
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path (Resolve-Path $HooksDirectory) $entry.Key), $entry.Value.Trim() + "`n", $encoding)
}

& git config --global core.hooksPath $HooksDirectory
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to configure global Git core.hooksPath.'
}

Write-Output "Installed global CodeOps Git hooks in: $HooksDirectory"
Write-Output "CodeOps root: $resolvedRoot"
Write-Output 'Enabled entrypoints: pre-commit, pre-push, post-merge'
Write-Output 'The database policy decides which imported projects actually run review.'
