$scriptPath = Join-Path $PSScriptRoot 'git-review-hook.ps1'
. $scriptPath -NoExecute

Describe 'database-driven Git review hook' {
    It 'runs only when the selected trigger is enabled' {
        $policy = [pscustomobject]@{
            enabled = $true
            preCommitEnabled = $false
            prePushEnabled = $true
            postMergeEnabled = $false
        }

        (Test-TriggerEnabled -Policy $policy -Trigger 'pre-push') | Should Be $true
        (Test-TriggerEnabled -Policy $policy -Trigger 'pre-commit') | Should Be $false
        (Test-TriggerEnabled -Policy $policy -Trigger 'post-merge') | Should Be $false
    }

    It 'skips every trigger for an unimported project' {
        $policy = [pscustomobject]@{ enabled = $false; prePushEnabled = $false; preCommitEnabled = $false; postMergeEnabled = $false }

        (Test-TriggerEnabled -Policy $policy -Trigger 'pre-push') | Should Be $false
    }

    It 'uses the policy threshold and fail-open settings' {
        $policy = [pscustomobject]@{ enabled = $true; failOnSeverity = 'MEDIUM'; failOpen = $true }

        $policy.failOnSeverity | Should Be 'MEDIUM'
        $policy.failOpen | Should Be $true
    }

    It 'preserves execution state after loading pre-push helpers' {
        $source = Get-Content -Raw $scriptPath

        $source | Should Match '\$script:CodeOpsExecuteHook = -not \[bool\]\$NoExecute'
        $source | Should Match 'if \(\$script:CodeOpsExecuteHook\)'
    }

    It 'reads the complete Windows repository root for policy lookup' {
        $root = (@(Invoke-GitOutput @('rev-parse', '--show-toplevel')))[0].ToString().Trim()

        $root | Should Be 'D:/development/project/my_learn'
    }

    It 'uses one LLM request and delegates grouping to the backend' {
        $source = Get-Content -Raw $scriptPath

        $source | Should Not Match 'New-ReviewBatches'
        $source | Should Not Match 'foreach \(\$batch in \$batches\)'
        $source | Should Match 'New-ReviewPayload -Repository \$RepositoryRoot'
    }
}
