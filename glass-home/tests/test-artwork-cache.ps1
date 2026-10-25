$ErrorActionPreference='Stop'
$workspaceRoot=(Resolve-Path "$PSScriptRoot/../../../..").Path
$jdk=Get-ChildItem (Join-Path $workspaceRoot '.local-tools/jdk17') -Directory | Select-Object -First 1
$testClasses=Join-Path $workspaceRoot '.local-tools/glasshome-artwork-test'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& "$($jdk.FullName)/bin/javac.exe" --release 8 -encoding UTF-8 -d $testClasses "$PSScriptRoot/../src/com/gathin/glasshome/ArtworkDiskCache.java" "$PSScriptRoot/ArtworkDiskCacheTest.java"
if($LASTEXITCODE -ne 0){throw 'Artwork cache test compilation failed'}
& "$($jdk.FullName)/bin/java.exe" -cp $testClasses com.gathin.glasshome.ArtworkDiskCacheTest
if($LASTEXITCODE -ne 0){throw 'Artwork cache regression test failed'}
