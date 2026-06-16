# Pulls E2E step screenshots from device/emulator to host.
# Source on device: /data/local/tmp/TonezenE2E/ (mirrored from app external files during test)
# Output: apps/android/app/build/e2e-screenshots/

$ErrorActionPreference = "Stop"
$androidRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $androidRoot "app\build\e2e-screenshots"

$device = (adb devices | Select-String "device$" | Select-Object -First 1).ToString().Split("`t")[0]
if (-not $device) {
    Write-Error "No Android device/emulator connected."
}

$remote = "/data/local/tmp/TonezenE2E"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$prevEap = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
& adb -s $device pull $remote $outputDir 2>&1 | Out-Null
$ErrorActionPreference = $prevEap

$count = (Get-ChildItem -Path $outputDir -Recurse -Filter "*.png" -ErrorAction SilentlyContinue | Measure-Object).Count
if ($count -gt 0) {
    Get-ChildItem -Path $outputDir -Recurse -Filter "*.png" | ForEach-Object {
        Write-Host $_.FullName
    }
    Write-Host "`n$count screenshot(s) in: $outputDir" -ForegroundColor Green
} else {
    Write-Warning "No screenshots at $remote. Run E2E tests with E2EScreenshots.capture first."
}
