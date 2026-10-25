$ErrorActionPreference='Continue'
$node=(Get-Command node.exe).Source
$runtime=Join-Path $PSScriptRoot '.runtime'
[IO.File]::WriteAllText((Join-Path $runtime 'runner.pid'),[string]$PID)
try {
    while($true) {
        & $node (Join-Path $PSScriptRoot 'server.mjs') 1>> (Join-Path $runtime 'service.log') 2>> (Join-Path $runtime 'error.log')
        Start-Sleep -Seconds 5
    }
} finally { Remove-Item -LiteralPath (Join-Path $runtime 'runner.pid') -Force -ErrorAction SilentlyContinue }
