# After agent edits Android files that cannot hot-reload on the emulator,
# queue installDebug when a device/emulator is connected.
# Debounced to avoid repeated builds during one agent turn.

$ErrorActionPreference = "SilentlyContinue"
$inputJson = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($inputJson)) { exit 0 }

try {
    $payload = $inputJson | ConvertFrom-Json
} catch {
    exit 0
}

$filePath = $payload.file_path
if (-not $filePath) { $filePath = $payload.path }
if (-not $filePath) { $filePath = $payload.filePath }
if (-not $filePath) { exit 0 }

$normalized = ($filePath -replace '\\', '/').ToLowerInvariant()
if ($normalized -notmatch '(^|/)apps/android/') { exit 0 }

$requiresReinstall = @(
    '/androidmanifest\.xml$'
    '/build\.gradle\.kts$'
    '/settings\.gradle\.kts$'
    '/gradle\.properties$'
    '/libs\.versions\.toml$'
    '/gradle/'
    '/res/'
    '/assets/'
    '/di/'
    '/data/local/.*entity\.kt$'
    '/data/local/.*database\.kt$'
    '/data/local/.*dao\.kt$'
    '/data/local/migrations/'
    '/playback/.*service'
    '/mainactivity\.kt$'
    '/.*application\.kt$'
    '/proguard'
    '/jni/'
    '/cpp/'
    '/\.pro$'
)

$matched = $false
foreach ($pattern in $requiresReinstall) {
    if ($normalized -match $pattern) {
        $matched = $true
        break
    }
}
if (-not $matched) { exit 0 }

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$debounceFile = Join-Path $PSScriptRoot ".android-install-debounce"
$debounceSeconds = 45
$now = Get-Date

if (Test-Path $debounceFile) {
    $last = Get-Content $debounceFile -ErrorAction SilentlyContinue | Get-Date -ErrorAction SilentlyContinue
    if ($last -and ($now - $last).TotalSeconds -lt $debounceSeconds) { exit 0 }
}
Set-Content -Path $debounceFile -Value $now.ToString("o") -NoNewline

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { exit 0 }

$devices = & adb devices 2>$null | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $devices) { exit 0 }

$androidDir = Join-Path $repoRoot "apps/android"
$worker = Join-Path $PSScriptRoot "run-android-install.ps1"

Start-Process -FilePath "powershell.exe" `
    -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $worker, "-AndroidDir", $androidDir) `
    -WindowStyle Hidden `
    -WorkingDirectory $repoRoot | Out-Null

exit 0
