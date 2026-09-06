Describe 'Docker LLM response timeouts' {
    It 'allows every proxy route to wait longer than a 600 second LLM response' {
        $nginx = Get-Content -Raw (Join-Path $PSScriptRoot '..\frontend\nginx.conf')
        $apiBlock = [regex]::Match($nginx, '(?s)location /api/ \{.*?\n    \}')
        $aiBlock = [regex]::Match($nginx, '(?s)location /api/ai/ \{.*?\n    \}')
        $apiBlock.Success | Should Be $true
        $aiBlock.Success | Should Be $true
        $apiBlock.Value | Should Match 'proxy_send_timeout\s+610s;'
        $apiBlock.Value | Should Match 'proxy_read_timeout\s+610s;'
        $aiBlock.Value | Should Match 'proxy_send_timeout\s+610s;'
        $aiBlock.Value | Should Match 'proxy_read_timeout\s+610s;'
    }

    It 'configures the LLM backend default response timeout as 600 seconds' {
        $compose = Get-Content -Raw (Join-Path $PSScriptRoot '..\docker-compose.yml')

        $compose | Should Match 'AI_TIMEOUT_SECONDS:\s*\$\{AI_TIMEOUT_SECONDS:-600\}'
    }

    It 'does not override the LLM response timeout to a lower value in the deployment environment' {
        $environment = Get-Content -Raw (Join-Path $PSScriptRoot '..\.env')

        $environment | Should Match '(?m)^AI_TIMEOUT_SECONDS=600$'
    }
}
