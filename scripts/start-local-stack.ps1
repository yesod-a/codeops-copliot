$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot

docker compose down --remove-orphans
if ($LASTEXITCODE -ne 0) {
    throw 'Existing Docker services failed to stop.'
}

docker compose up -d --build mysql llm-backend frontend
if ($LASTEXITCODE -ne 0) {
    throw 'Docker services failed to start.'
}

$env:AI_BACKEND_ENABLED = 'true'
$env:AI_BACKEND_URL = 'http://127.0.0.1:8090'
$mysqlDatabase = 'codeops'
$mysqlUser = 'codeops'
$mysqlPassword = 'codeops'
$mysqlPort = '3307'
if (Test-Path (Join-Path $projectRoot '.env')) {
    foreach ($line in Get-Content (Join-Path $projectRoot '.env')) {
        if ($line -match '^\s*MYSQL_DATABASE\s*=\s*(.*)\s*$') { $mysqlDatabase = $Matches[1].Trim() }
        if ($line -match '^\s*MYSQL_USER\s*=\s*(.*)\s*$') { $mysqlUser = $Matches[1].Trim() }
        if ($line -match '^\s*MYSQL_PASSWORD\s*=\s*(.*)\s*$') { $mysqlPassword = $Matches[1].Trim() }
        if ($line -match '^\s*MYSQL_PORT\s*=\s*(.*)\s*$') { $mysqlPort = $Matches[1].Trim() }
    }
}
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$mysqlPort/$mysqlDatabase?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
$env:SPRING_DATASOURCE_USERNAME = $mysqlUser
$env:SPRING_DATASOURCE_PASSWORD = $mysqlPassword

Set-Location (Join-Path $projectRoot 'backened')
mvn spring-boot:run
