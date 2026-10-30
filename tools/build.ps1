$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
Set-Location (Split-Path -Parent $PSScriptRoot)
if ($args.Count -gt 0) { $tasks = $args } else { $tasks = @("build", "-x", "test") }
.\gradlew.bat @tasks --offline *> build-gradle.log
"EXIT=$LASTEXITCODE" | Add-Content build-gradle.log
