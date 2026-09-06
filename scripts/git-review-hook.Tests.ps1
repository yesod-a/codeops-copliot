$scriptPath = Join-Path $PSScriptRoot 'git-review-hook.ps1'
$modulePath = Join-Path $PSScriptRoot 'codeops-client.psm1'

Describe 'Script-only CodeOps Git hook' {
    It 'does not depend on a localhost client or repository-local configuration' {
        $source = Get-Content -Raw $scriptPath
        $source | Should Not Match '127\.0\.0\.1:49152'
        $source | Should Not Match '/v1/reviews/'
        $source | Should Not Match '\.codeops\\client\.json'
        $source | Should Not Match 'repositoryPath\s*='
        $source | Should Match '/api/client/projects/resolve'
        $source | Should Match '/api/agent/review-tasks'
        $source | Should Match '推送已放行'
        $source | Should Match 'Get-CodeOpsRemoteUrl'
    }

    It 'stores client state outside repositories and encrypts the token' {
        $oldLocalAppData = $env:LOCALAPPDATA
        try {
            $clientState = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
            $env:LOCALAPPDATA = $clientState
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'initialize-codeops-client.ps1') `
                -ServerUrl 'http://localhost:5173' -AgentToken 'cop_test_secret' -SkipHookInstall | Out-Null
            $clientDir = Join-Path $clientState 'CodeOps'
            $settingsPath = Join-Path $clientDir 'settings.json'
            $credentialsPath = Join-Path $clientDir 'credentials.dat'
            Test-Path $settingsPath | Should Be $true
            Test-Path $credentialsPath | Should Be $true
            Test-Path (Join-Path $clientState '.codeops\client.json') | Should Be $false
            $settings = Get-Content -Raw $settingsPath | ConvertFrom-Json
            $settings.serverUrl | Should Be 'http://localhost:5173'
            ($settings.PSObject.Properties.Name -contains 'agentToken') | Should Be $false
            (Get-Content -Raw $credentialsPath) | Should Not Be 'cop_test_secret'
        } finally { $env:LOCALAPPDATA = $oldLocalAppData }
    }

    It 'round-trips the DPAPI token without relying on security cmdlet autoloading' {
        $source = Get-Content -Raw $modulePath
        $source | Should Not Match 'Convert(To|From)-SecureString'

        $oldLocalAppData = $env:LOCALAPPDATA
        try {
            $env:LOCALAPPDATA = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'initialize-codeops-client.ps1') `
                -ServerUrl 'http://localhost:5173' -AgentToken 'cop_test_secret' -SkipHookInstall | Out-Null
            $token = & powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Import-Module '$modulePath' -Force; Get-CodeOpsAgentToken"
            $token | Should Be 'cop_test_secret'
        } finally { $env:LOCALAPPDATA = $oldLocalAppData }
    }

    It 'builds central requests without an absolute local path' {
        Import-Module $modulePath -Force
        $resolution = New-CodeOpsProjectResolutionPayload -RemoteUrl 'git@git.example.com:acme/order.git'
        $resolution.remoteUrl | Should Be 'git@git.example.com:acme/order.git'
        ($resolution.PSObject.Properties.Name -contains 'repositoryPath') | Should Be $false
        $review = New-CodeOpsReviewPayload -ProjectId 3 -RepositoryKey 'git.example.com/acme/order' `
            -Trigger 'pre-push' -Branch 'main' -HeadCommit ('1' * 40) -BaseRef ('0' * 40) `
            -Files @([pscustomobject]@{ Path = 'src/App.java'; GitStatus = 'M'; Additions = 1; Deletions = 0; Patch = '@@'; })
        $review.repositoryKey | Should Be 'git.example.com/acme/order'
        ($review.PSObject.Properties.Name -contains 'repositoryPath') | Should Be $false
    }

    It 'uses an unregistered remote as a no-op and blocks forbidden central responses' {
        Import-Module $modulePath -Force
        (Resolve-CodeOpsResolutionAction -Response ([pscustomobject]@{ projectId = $null; reviewEnabled = $false })) | Should Be 'SKIP'
        (Resolve-CodeOpsResolutionAction -Response ([pscustomobject]@{ projectId = 3; reviewEnabled = $true; role = 'REVIEWER' })) | Should Be 'REVIEW'
        $forbidden = $false
        try { Resolve-CodeOpsResolutionAction -Response ([pscustomobject]@{ projectId = 3; reviewEnabled = $true; role = 'VIEWER' }) } catch { $forbidden = $true }
        $forbidden | Should Be $true
    }

    It 'collects staged and commit-range changes from a Git repository' {
        Import-Module $modulePath -Force
        $repository = Join-Path $TestDrive 'repository'
        New-Item -ItemType Directory -Path $repository | Out-Null
        $isolatedHooks = Join-Path $TestDrive 'hooks'
        New-Item -ItemType Directory -Path $isolatedHooks | Out-Null
        & git -C $repository init | Out-Null
        & git -C $repository config user.email 'codeops-test@example.test'
        & git -C $repository config user.name 'CodeOps Test'
        & git -C $repository config core.hooksPath $isolatedHooks
        [IO.File]::WriteAllText((Join-Path $repository 'Sample.java'), "class Sample {}`n", (New-Object Text.UTF8Encoding($false)))
        & git -C $repository add Sample.java
        & git -C $repository commit -m 'initial' | Out-Null
        $baseSha = (& git -C $repository rev-parse HEAD).Trim()

        [IO.File]::WriteAllText((Join-Path $repository 'Sample.java'), "class Sample { int value = 1; }`n", (New-Object Text.UTF8Encoding($false)))
        & git -C $repository add Sample.java
        $staged = @(Get-CodeOpsChangedFiles -RepositoryRoot $repository -Staged)
        $staged.Count | Should Be 1
        $staged[0].Path | Should Be 'Sample.java'
        $staged[0].Patch | Should Match 'int value = 1'

        & git -C $repository commit -m 'change' | Out-Null
        $headSha = (& git -C $repository rev-parse HEAD).Trim()
        $committed = @(Get-CodeOpsChangedFiles -RepositoryRoot $repository -BaseSha $baseSha -HeadSha $headSha)
        $committed.Count | Should Be 1
        $committed[0].Additions | Should Be 1
        $committed[0].Deletions | Should Be 1
    }

    It 'initializes the Hook failure policy before configuration is loaded' {
        $source = Get-Content -Raw $scriptPath
        $source | Should Match '\$settings\s*=\s*\$null'
        $source | Should Match '\$script:CodeOpsResolvedFailOpen\s*=\s*\$false'
    }

    It 'chains and restores a pre-existing global hook path' {
        $installer = Get-Content -Raw (Join-Path $PSScriptRoot 'install-pre-push-hook.ps1')
        $uninstaller = Get-Content -Raw (Join-Path $PSScriptRoot 'uninstall-pre-push-hook.ps1')
        $installer | Should Match 'previousHooksPath'
        $installer | Should Match 'existingState'
        $installer | Should Match 'ORIGINAL_HOOKS_PATH'
        $uninstaller | Should Match 'previousHooksPath'
        $uninstaller | Should Match 'git config --global core\.hooksPath \$previous'
    }

    It 'does not recursively chain CodeOps hooks when reinstalled' {
        $oldLocalAppData = $env:LOCALAPPDATA
        $oldGitConfigGlobal = $env:GIT_CONFIG_GLOBAL
        try {
            $env:LOCALAPPDATA = Join-Path $TestDrive 'client-state'
            $env:GIT_CONFIG_GLOBAL = Join-Path $TestDrive 'gitconfig'
            $hooks = Join-Path $env:LOCALAPPDATA 'CodeOps\git-hooks'
            $installerPath = Join-Path $PSScriptRoot 'install-pre-push-hook.ps1'

            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installerPath `
                -CodeOpsRoot (Join-Path $PSScriptRoot '..') -HooksDirectory $hooks | Out-Null
            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $installerPath `
                -CodeOpsRoot (Join-Path $PSScriptRoot '..') -HooksDirectory $hooks -Force | Out-Null

            $hook = Get-Content -Raw (Join-Path $hooks 'pre-commit')
            $hook | Should Not Match ([regex]::Escape($hooks.Replace('\', '/')))
            $hook | Should Match "ORIGINAL_HOOKS_PATH=''"
        } finally {
            $env:LOCALAPPDATA = $oldLocalAppData
            $env:GIT_CONFIG_GLOBAL = $oldGitConfigGlobal
        }
    }

    It 'does not chain a legacy CodeOps hook directory' {
        $oldLocalAppData = $env:LOCALAPPDATA
        $oldGitConfigGlobal = $env:GIT_CONFIG_GLOBAL
        try {
            $testRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString('N'))
            $env:LOCALAPPDATA = Join-Path $testRoot 'client-state'
            $env:GIT_CONFIG_GLOBAL = Join-Path $testRoot 'gitconfig'
            $legacyHooks = Join-Path $testRoot 'legacy-codeops-hooks'
            $managedHooks = Join-Path $env:LOCALAPPDATA 'CodeOps\git-hooks'
            New-Item -ItemType Directory -Path $legacyHooks | Out-Null
            [IO.File]::WriteAllText((Join-Path $legacyHooks 'pre-commit'), '#!/bin/sh' + "`n" + 'exec powershell.exe -File "D:/CodeOps/scripts/git-review-hook.ps1"', (New-Object Text.UTF8Encoding($false)))
            & git config --global core.hooksPath $legacyHooks

            & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'install-pre-push-hook.ps1') `
                -CodeOpsRoot (Join-Path $PSScriptRoot '..') -HooksDirectory $managedHooks | Out-Null
            $LASTEXITCODE | Should Be 0

            $hook = Get-Content -Raw (Join-Path $managedHooks 'pre-commit')
            $hook | Should Match "ORIGINAL_HOOKS_PATH=''"
        } finally {
            $env:LOCALAPPDATA = $oldLocalAppData
            $env:GIT_CONFIG_GLOBAL = $oldGitConfigGlobal
        }
    }
}
