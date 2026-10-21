param([string]$SdkRoot, [string]$JavaHome = $env:JAVA_HOME, [string]$ApplicationId = 'com.gathin.glasshome')
$ErrorActionPreference = 'Stop'
if (-not $SdkRoot) { $SdkRoot = Join-Path (Resolve-Path "$PSScriptRoot/../../../.local-tools").Path 'android-sdk' }
if (-not $JavaHome) {
    $bundledJdk = Get-ChildItem "$PSScriptRoot/../../../.local-tools/jdk17" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    $JavaHome = if ($bundledJdk) { $bundledJdk.FullName } else { Split-Path (Split-Path (Get-Command javac.exe).Source) }
}
$tools = Join-Path $SdkRoot 'build-tools/34.0.0'
if (-not (Test-Path "$tools/aapt2.exe")) { $tools = Join-Path $SdkRoot 'build-tools/android-14' }
$android = Join-Path $SdkRoot 'platforms/android-34/android.jar'
if (-not (Test-Path $android)) { throw "Android SDK 34 missing at $android" }
if (-not (Test-Path "$tools/aapt2.exe")) { throw "Build tools missing at $tools" }
$build = Join-Path (Split-Path $SdkRoot) 'glasshome-build'
$output = Join-Path $PSScriptRoot 'output'
New-Item -ItemType Directory -Force -Path "$build/classes","$build/generated","$build/dex","$build/input",$output,"$PSScriptRoot/.signing" | Out-Null
# Some Windows Android binaries cannot open Unicode source paths. Stage inputs in the SDK's ASCII parent.
Copy-Item -LiteralPath "$PSScriptRoot/res","$PSScriptRoot/src","$PSScriptRoot/assets","$PSScriptRoot/AndroidManifest.xml" -Destination "$build/input" -Recurse -Force
[xml]$manifest = Get-Content "$build/input/AndroidManifest.xml"
$manifest.manifest.package = $ApplicationId
$manifest.Save("$build/input/AndroidManifest.xml")
$apkName = if ($ApplicationId -eq 'com.gathin.glasshome') { 'glass-home.apk' } else { 'glass-home-xiaomi.apk' }
function Run([string]$Exe, [string[]]$Arguments) { & $Exe @Arguments; if ($LASTEXITCODE -ne 0) { throw "$Exe exited $LASTEXITCODE" } }
Run "$tools/aapt2.exe" @('compile','--dir',"$build/input/res",'-o',"$build/resources.zip")
Run "$tools/aapt2.exe" @('link','-I',$android,'--manifest',"$build/input/AndroidManifest.xml",'--java',"$build/generated",'--custom-package','com.gathin.glasshome','-A',"$build/input/assets",'-0','ttf','-0','otf','-o',"$build/unsigned.apk",'-R',"$build/resources.zip",'--auto-add-overlay')
$sources = @(Get-ChildItem "$build/input/src","$build/generated" -Filter '*.java' -Recurse | ForEach-Object FullName)
Run "$JavaHome/bin/javac.exe" (@('--release','8','-encoding','UTF-8','-classpath',$android,'-d',"$build/classes") + $sources)
Run "$JavaHome/bin/jar.exe" @('--create','--file',"$build/classes.jar",'-C',"$build/classes",'.')
Run "$JavaHome/bin/java.exe" @('-cp',"$tools/lib/d8.jar",'com.android.tools.r8.D8','--lib',$android,'--min-api','26','--output',"$build/dex","$build/classes.jar")
Run "$JavaHome/bin/jar.exe" @('--update','--file',"$build/unsigned.apk",'-C',"$build/dex",'classes.dex')
Run "$tools/zipalign.exe" @('-f','-p','4',"$build/unsigned.apk","$build/aligned.apk")
$key = "$PSScriptRoot/.signing/glasshome.keystore"
if (-not (Test-Path $key)) { Run "$JavaHome/bin/keytool.exe" @('-genkeypair','-keystore',$key,'-alias','glasshome','-storepass','android','-keypass','android','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Glass Home Local') }
Run "$JavaHome/bin/java.exe" @('-jar',"$tools/lib/apksigner.jar",'sign','--ks',$key,'--ks-key-alias','glasshome','--ks-pass','pass:android','--key-pass','pass:android','--out',"$output/$apkName","$build/aligned.apk")
Run "$JavaHome/bin/java.exe" @('-jar',"$tools/lib/apksigner.jar",'verify','--verbose',"$output/$apkName")
Write-Output "APK: $output/$apkName"
