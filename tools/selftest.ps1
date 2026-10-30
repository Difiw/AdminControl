$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
Set-Location (Split-Path -Parent $PSScriptRoot)
Remove-Item -Recurse -Force run-test, run-test-lumira, run\lumira-selftest.txt -ErrorAction SilentlyContinue
.\gradlew.bat runLumiraTest --offline *> selftest-gradle.log
"EXIT=$LASTEXITCODE" | Add-Content selftest-gradle.log
