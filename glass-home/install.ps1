param([Parameter(Mandatory=$true)][string]$TvHost, [string]$WallpaperFile)
$ErrorActionPreference='Stop'
$adb=(Resolve-Path "$PSScriptRoot/../../../.local-tools/platform-tools/adb.exe").Path
$serial="${TvHost}:5555"
function Adb([string[]]$Arguments) { $result=& $adb -s $serial @Arguments; if($LASTEXITCODE -ne 0){throw "ADB failed: $Arguments"}; return $result }
Adb @('install','-r',"$PSScriptRoot/output/glass-home.apk")
Adb @('shell','pm','grant','com.gathin.glasshome','android.permission.WRITE_SECURE_SETTINGS')
Adb @('shell','cmd','notification','allow_listener','com.gathin.glasshome/com.gathin.glasshome.RoutingLifecycleService')
Adb @('shell','am','broadcast','-a','com.gathin.glasshome.CONFIGURE_ROUTING','-n','com.gathin.glasshome/.RoutingReceiver','--ez','enabled','true')
Adb @('shell','am','start','-W','-n','com.gathin.glasshome/.GlassHomeActivity')
if($WallpaperFile) { Adb @('push',$WallpaperFile,'/sdcard/Android/data/com.gathin.glasshome/files/aerial.mov') }
$old=(Adb @('shell','settings','get','secure','enabled_accessibility_services') | Out-String).Trim()
$services=@(); if($old -and $old -ne 'null'){$services=@($old.Split(':'))}
$own='com.gathin.glasshome/com.gathin.glasshome.HomeGuardService'
if($services -notcontains $own){$services+= $own}
Adb @('shell','settings','put','secure','enabled_accessibility_services',($services -join ':'))
Adb @('shell','settings','put','secure','accessibility_enabled','1')
Adb @('shell','input','keyevent','3')
Start-Sleep -Seconds 2
Adb @('shell','dumpsys','window') | Select-String 'mCurrentFocus'
