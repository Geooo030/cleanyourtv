param([Parameter(Mandatory=$true)][string]$TvHost)
$ErrorActionPreference='Stop'
$adb=(Resolve-Path "$PSScriptRoot/../../../.local-tools/platform-tools/adb.exe").Path
$serial="${TvHost}:5555"
& $adb -s $serial shell am broadcast -a com.gathin.glasshome.CONFIGURE_ROUTING -n com.gathin.glasshome/.RoutingReceiver --ez enabled false
& $adb -s $serial shell cmd notification disallow_listener com.gathin.glasshome/com.gathin.glasshome.RoutingLifecycleService
$old=(& $adb -s $serial shell settings get secure enabled_accessibility_services | Out-String).Trim()
if($LASTEXITCODE -ne 0){throw 'Cannot read TV accessibility state'}
$own='com.gathin.glasshome/com.gathin.glasshome.HomeGuardService'
$remaining=@($old.Split(':') | Where-Object {$_ -and $_ -ne 'null' -and $_ -ne $own})
if($remaining.Count) { & $adb -s $serial shell settings put secure enabled_accessibility_services ($remaining -join ':') }
else { & $adb -s $serial shell settings delete secure enabled_accessibility_services }
if($LASTEXITCODE -ne 0){throw 'Cannot remove Glass Home routing service'}
& $adb -s $serial shell cmd package set-home-activity --user 0 com.mitv.tvhome/.MainActivityUserMode
& $adb -s $serial shell input keyevent 3
& $adb -s $serial shell pm revoke com.gathin.glasshome android.permission.WRITE_SECURE_SETTINGS
