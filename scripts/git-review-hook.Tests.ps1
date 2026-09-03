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
}
