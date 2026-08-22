$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$extendedUiWrapper = Join-Path $repoRoot "..\ExtendedUI\gradlew.bat"

if (Test-Path $extendedUiWrapper) {
    Push-Location $repoRoot
    try {
        & $extendedUiWrapper -p $repoRoot wrapper --gradle-version 9.7.1 --distribution-type bin --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle wrapper generation failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "Generated the Sanctuary Gradle wrapper using the sibling ExtendedUI wrapper."
    exit 0
}

throw "Could not find ..\ExtendedUI\gradlew.bat. Generate the Gradle 9.7.1 wrapper from IntelliJ/Gradle or place Sanctuary beside ExtendedUI first."
