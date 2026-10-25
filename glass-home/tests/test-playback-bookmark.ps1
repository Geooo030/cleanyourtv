$ErrorActionPreference='Stop'
$workspaceRoot=(Resolve-Path "$PSScriptRoot/../../../..").Path
$jdk=Get-ChildItem (Join-Path $workspaceRoot '.local-tools/jdk17') -Directory | Select-Object -First 1
$testClasses=Join-Path $workspaceRoot '.local-tools/glasshome-bookmark-test'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
& "$($jdk.FullName)/bin/javac.exe" --release 8 -encoding UTF-8 -d $testClasses "$PSScriptRoot/../src/com/gathin/glasshome/PlaybackBookmark.java" "$PSScriptRoot/PlaybackBookmarkTest.java"
if($LASTEXITCODE -ne 0){throw 'Playback bookmark test compilation failed'}
& "$($jdk.FullName)/bin/java.exe" -cp $testClasses com.gathin.glasshome.PlaybackBookmarkTest
if($LASTEXITCODE -ne 0){throw 'Playback bookmark regression test failed'}
