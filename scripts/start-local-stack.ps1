$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot

if (-not (Test-Path (Join-Path $projectRoot '.env'))) {
    throw 'Missing .env. Copy .env.example to .env and configure AI_API_KEY plus CODEOPS_BOOTSTRAP_TOKEN.'
}

docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    throw 'CodeOps Docker services failed to start.'
}

Write-Output 'CodeOps is running at http://127.0.0.1:5173'
