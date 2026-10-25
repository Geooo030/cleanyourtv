param([string]$TvHost='192.168.31.81',[string]$ComputerHost='192.168.31.220',[switch]$AutoStart)
$ErrorActionPreference='Stop'
$runtime=Join-Path $PSScriptRoot '.runtime'
& "$PSScriptRoot/start.ps1"
for($i=0;$i -lt 20 -and !(Test-Path -LiteralPath "$runtime/config.json");$i++){Start-Sleep -Milliseconds 300}
$config=Get-Content -LiteralPath "$runtime/config.json" -Raw | ConvertFrom-Json
$adb=(Resolve-Path "$PSScriptRoot/../../../../.local-tools/platform-tools/adb.exe").Path
$output=& $adb -s "${TvHost}:5555" shell am broadcast -n com.gathin.glasshome/.MusicConfigReceiver -a com.gathin.glasshome.CONFIGURE_MUSIC --es base "http://${ComputerHost}:5176" --es token $config.token
if($LASTEXITCODE -ne 0 -or ($output -join '') -notmatch 'result=-1'){throw 'TV pairing failed'}
$identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $runtime /inheritance:r /grant:r "${identity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if($AutoStart){
    $startup=[Environment]::GetFolderPath('Startup')
    $shell=New-Object -ComObject WScript.Shell
    $shortcut=$shell.CreateShortcut((Join-Path $startup 'Glass Home Music.lnk'))
    $shortcut.TargetPath=(Get-Command powershell.exe).Source
    $shortcut.Arguments='-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "'+(Join-Path $PSScriptRoot 'start.ps1')+'"'
    $shortcut.WindowStyle=7;$shortcut.WorkingDirectory=$PSScriptRoot;$shortcut.Save()
}
Write-Output 'Music service paired with TV; account cookie remains encrypted on this computer.'
