$ErrorActionPreference='Stop'
$runtime=Join-Path $PSScriptRoot '.runtime'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
if(Get-NetTCPConnection -LocalPort 5176 -State Listen -ErrorAction SilentlyContinue){exit 0}
$runner=Join-Path $runtime 'runner.pid'
if(Test-Path -LiteralPath $runner){$runnerPid=[int](Get-Content -LiteralPath $runner);if(Get-Process -Id $runnerPid -ErrorAction SilentlyContinue){exit 0}}
Start-Process -FilePath (Get-Command powershell.exe).Source -ArgumentList @('-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File','"'+(Join-Path $PSScriptRoot 'run.ps1')+'"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden
