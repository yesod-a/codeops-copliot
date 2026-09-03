$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$apiBase = 'http://127.0.0.1:8080'
$repositoryPath = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $false)]$Body
    )

    $params = @{
        Method = $Method
        Uri = $Uri
        Headers = @{ Accept = 'application/json' }
    }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    Invoke-RestMethod @params
}

$scan = Invoke-JsonRequest -Method Post -Uri "$apiBase/api/repositories/scan" -Body @{
    repositoryPath = $repositoryPath
    scope = 'WORKTREE'
    baseRef = $null
}

if ($null -eq $scan.files) {
    throw 'Scan response did not contain files.'
}

$selected = @($scan.files | Where-Object { $_.supported -ne $false -and $_.binary -ne $true } | Select-Object -First 1)
if ($selected.Count -ne 1) {
    throw 'Scan did not return a supported non-binary file.'
}

$selectedPath = [string]$selected[0].path
$task = Invoke-JsonRequest -Method Post -Uri "$apiBase/api/reviews/from-git" -Body @{
    repositoryPath = $repositoryPath
    scope = 'WORKTREE'
    baseRef = $null
    title = 'Local Git smoke review'
    files = @($selectedPath)
}

if ($null -eq $task.id) {
    throw 'Git review response did not contain a task id.'
}

$deadline = (Get-Date).AddSeconds(10)
$latest = $task
while ($latest.status -notin @('COMPLETED', 'FAILED') -and (Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 200
    $latest = Invoke-JsonRequest -Method Get -Uri "$apiBase/api/reviews/$($task.id)"
}

if ($latest.status -notin @('COMPLETED', 'FAILED')) {
    throw "Review did not reach a terminal state within 10 seconds. Status: $($latest.status)"
}

$reviewedFiles = @($latest.files)
if ($reviewedFiles.Count -ne 1 -or [string]$reviewedFiles[0].path -ne $selectedPath) {
    throw "Expected exactly one reviewed file ($selectedPath), received $($reviewedFiles.Count)."
}

Write-Host "Local Git smoke review passed: $($latest.status), file=$selectedPath, task=$($task.id)"
