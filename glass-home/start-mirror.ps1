param(
    [string]$TvHost,
    [string]$AdbPath,
    [ValidateRange(1, 65535)][int]$Port = 5173
)
$ErrorActionPreference = 'Stop'
$previousHost = $env:TV_HOST
$previousAdb = $env:ADB_PATH
$previousPort = $env:PORT
Push-Location (Join-Path $PSScriptRoot 'tv-mirror')
try {
    if ($TvHost) { $env:TV_HOST = $TvHost }
    if ($AdbPath) { $env:ADB_PATH = $AdbPath }
    $env:PORT = [string]$Port
    if (-not (Test-Path -LiteralPath 'node_modules')) {
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    }
    Write-Host "Open http://localhost:$Port in Chrome or Edge. Press Ctrl+C to stop."
    & npm run dev
    if ($LASTEXITCODE -ne 0) { throw 'TV Mirror stopped with an error' }
} finally {
    $env:TV_HOST = $previousHost
    $env:ADB_PATH = $previousAdb
    $env:PORT = $previousPort
    Pop-Location
}
