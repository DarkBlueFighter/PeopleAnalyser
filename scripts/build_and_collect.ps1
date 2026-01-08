# Build the Android project locally and collect debug APK
# Usage: Open PowerShell in project root and run: .\scripts\build_and_collect.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Write-Host "Starting local build and collect script"

function Check-Command($name) {
    return (Get-Command $name -ErrorAction SilentlyContinue) -ne $null
}

# Check Java
if (-not (Check-Command "java")) {
    Write-Error "Java is not available in PATH. Please install JDK and ensure 'java -version' works."
    exit 1
}

# Show versions (capture stderr as java prints version to stderr sometimes)
try {
    $javaVersionRaw = & java -version 2>&1 | Out-String
    $javaVersionLines = $javaVersionRaw -split "`n" | Select-Object -First 3
    Write-Host "java:"; $javaVersionLines | ForEach-Object { Write-Host $_ }
} catch {
    Write-Warning "Unable to determine Java version: $_"
}

if (Test-Path .\gradlew.bat) {
    Write-Host "Using project gradle wrapper"
} else {
    Write-Error "gradlew.bat not found in project root. Ensure you're in the project root and gradle wrapper exists."; exit 1
}

# Run assembleDebug
Write-Host "Running .\gradlew.bat assembleDebug (this may take a while)"
$proc = Start-Process -FilePath .\gradlew.bat -ArgumentList 'assembleDebug','--no-daemon','--console=plain' -NoNewWindow -Wait -PassThru -RedirectStandardOutput .\gradle_stdout.txt -RedirectStandardError .\gradle_stderr.txt
$exitCode = $proc.ExitCode

# Output captured logs to console (tail last 200 lines)
if (Test-Path .\gradle_stdout.txt) { Write-Host "--- gradle stdout (tail) ---"; Get-Content .\gradle_stdout.txt -Tail 200 | ForEach-Object { Write-Host $_ } }
if (Test-Path .\gradle_stderr.txt) { Write-Host "--- gradle stderr (tail) ---"; Get-Content .\gradle_stderr.txt -Tail 200 | ForEach-Object { Write-Host $_ } }

if ($exitCode -ne 0) {
    Write-Error "Gradle build failed with exit code $exitCode. See output above for details."; exit $exitCode
}

# Collect APK(s)
$apkPath = Get-ChildItem -Path .\app\build\outputs\apk\debug -Filter *.apk -Recurse -ErrorAction SilentlyContinue | Select-Object -Last 1
if (-not $apkPath) {
    Write-Error "No debug APK found under app/build/outputs/apk/debug/. Build may have failed."; exit 1
}

$artifactDir = Join-Path -Path (Get-Location) -ChildPath "artifacts"
if (-not (Test-Path $artifactDir)) { New-Item -ItemType Directory -Path $artifactDir | Out-Null }
$dest = Join-Path $artifactDir $apkPath.Name
Copy-Item -Path $apkPath.FullName -Destination $dest -Force
Write-Host "APK collected: $dest"
Write-Host "Done."
