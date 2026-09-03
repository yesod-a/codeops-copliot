$scriptPath = Join-Path $PSScriptRoot 'pre-push-review.ps1'
. $scriptPath -NoExecute

Describe 'pre-push review helpers' {
    It 'parses a branch update from Git pre-push input' {
        $update = ConvertFrom-PrePushLine 'refs/heads/feature refs/heads/main 1111111111111111111111111111111111111111 2222222222222222222222222222222222222222'

        $update.LocalRef | Should Be 'refs/heads/feature'
        $update.RemoteRef | Should Be 'refs/heads/main'
        $update.OldSha | Should Be '1111111111111111111111111111111111111111'
        $update.NewSha | Should Be '2222222222222222222222222222222222222222'
    }

    It 'skips deleted remote refs' {
        $update = ConvertFrom-PrePushLine 'refs/heads/feature refs/heads/main 2222222222222222222222222222222222222222 0000000000000000000000000000000000000000'

        $update.IsDeletion | Should Be $true
    }

    It 'blocks findings at or above the configured severity' {
        $findings = @(
            [pscustomobject]@{ severity = 'LOW'; message = '低风险' },
            [pscustomobject]@{ severity = 'HIGH'; message = '高风险' },
            [pscustomobject]@{ severity = 'CRITICAL'; message = '严重风险' }
        )

        $blocking = @(Get-BlockingFindings -Findings $findings -FailOnSeverity 'HIGH')

        $blocking.Count | Should Be 2
        $blocking[0].severity | Should Be 'HIGH'
        $blocking[1].severity | Should Be 'CRITICAL'
    }

    It 'allows all findings when the threshold is above critical' {
        $findings = @([pscustomobject]@{ severity = 'CRITICAL'; message = '严重风险' })

        @(Get-BlockingFindings -Findings $findings -FailOnSeverity 'OFF').Count | Should Be 0
    }
}
