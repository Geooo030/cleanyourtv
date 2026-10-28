$ErrorActionPreference='Stop'
$workspaceRoot=(Resolve-Path "$PSScriptRoot/../../../..").Path
$jdk=Get-ChildItem (Join-Path $workspaceRoot '.local-tools/jdk17') -Directory | Select-Object -First 1
$testClasses=Join-Path $workspaceRoot '.local-tools/glasshome-home-key-test'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& "$($jdk.FullName)/bin/javac.exe" --release 8 -encoding UTF8 -d $testClasses "$PSScriptRoot/../src/com/gathin/glasshome/HomeKeyRouter.java" "$PSScriptRoot/HomeKeyRouterTest.java"
if($LASTEXITCODE -ne 0){throw 'Home key test compilation failed'}
& "$($jdk.FullName)/bin/java.exe" -cp $testClasses com.gathin.glasshome.HomeKeyRouterTest
if($LASTEXITCODE -ne 0){throw 'Home key regression failed'}
