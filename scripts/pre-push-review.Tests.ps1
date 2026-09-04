$scriptPath = Join-Path $PSScriptRoot 'pre-push-review.ps1'
. $scriptPath -NoExecute

Describe 'pre-push review helpers' {
    It 'initializes UTF-8 for PowerShell output' {
        $oldOutputEncoding = $global:OutputEncoding
        $oldConsoleEncoding = [Console]::OutputEncoding
        try {
            $global:OutputEncoding = [Text.Encoding]::ASCII
            [Console]::OutputEncoding = [Text.Encoding]::ASCII

            Set-CodeOpsUtf8Output

            $global:OutputEncoding.CodePage | Should Be 65001
            [Console]::OutputEncoding.CodePage | Should Be 65001
        } finally {
            $global:OutputEncoding = $oldOutputEncoding
            [Console]::OutputEncoding = $oldConsoleEncoding
        }
    }

    It 'decodes Chinese JSON responses as UTF-8 bytes' {
        $bytes = [Text.Encoding]::UTF8.GetBytes('{"message":"Git 操作失败"}')

        $response = ConvertFrom-CodeOpsJsonBytes -Bytes $bytes

        $response.message | Should Be 'Git 操作失败'
    }

    It 'parses a branch update from Git pre-push input' {
        $update = ConvertFrom-PrePushLine 'refs/heads/feature 2222222222222222222222222222222222222222 refs/heads/main 1111111111111111111111111111111111111111'

        $update.LocalRef | Should Be 'refs/heads/feature'
        $update.RemoteRef | Should Be 'refs/heads/main'
        $update.OldSha | Should Be '1111111111111111111111111111111111111111'
        $update.NewSha | Should Be '2222222222222222222222222222222222222222'
    }

    It 'skips deleted remote refs' {
        $update = ConvertFrom-PrePushLine 'refs/heads/feature 0000000000000000000000000000000000000000 refs/heads/main 2222222222222222222222222222222222222222'

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

    It 'does not treat binary artifacts as reviewable files' {
        (Test-ReviewablePath 'llm-backend/app/__pycache__/main.cpython-310.pyc') | Should Be $false
        (Test-ReviewablePath 'backened/src/main/App.java') | Should Be $true
    }

    It 'builds one review payload containing every file' {
        $files = @(
            [pscustomobject]@{ Path = 'A.java'; Patch = '修复一' },
            [pscustomobject]@{ Path = 'B.java'; Patch = '修复二' }
        )

        $payload = New-ReviewPayload -Repository 'D:/repo' -Title '一次评审' -Files $files
        $request = $payload | ConvertFrom-Json

        $request.files.Count | Should Be 2
        $request.files[0].path | Should Be 'A.java'
        $request.files[1].path | Should Be 'B.java'
    }

    It 'truncates each file using the configured file limit' {
        $oldLimit = $env:CODEOPS_REVIEW_FILE_CHARS
        try {
            $env:CODEOPS_REVIEW_FILE_CHARS = '4'
            $payload = New-ReviewPayload -Repository 'D:/repo' -Title '限制' -Files @([pscustomobject]@{ Path = 'A.java'; Patch = '123456' })
            ((($payload | ConvertFrom-Json).files)[0].content).Length | Should Be 4
        } finally {
            $env:CODEOPS_REVIEW_FILE_CHARS = $oldLimit
        }
    }
}
