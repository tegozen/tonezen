param(
    [Parameter(Mandatory = $true)]
    [string]$AndroidDir
)

$ErrorActionPreference = "Continue"
Set-Location $AndroidDir

& .\gradlew.bat :app:installDebug --quiet
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$device = (& adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' } | Select-Object -First 1)
if ($device) {
    & adb shell am start -n com.tonezen.app/.MainActivity | Out-Null
}

exit 0
