# Runs instrumented E2E tests with adb screenrecord (one video per test method).
# Output: apps/android/app/build/e2e-recordings/<Class>_<method>.mp4
param(
    [string[]]$OnlyTests
)

$ErrorActionPreference = "Stop"
$androidRoot = Split-Path -Parent $PSScriptRoot
Set-Location $androidRoot

$device = (adb devices | Select-String "device$" | Select-Object -First 1).ToString().Split("`t")[0]
if (-not $device) {
    Write-Error "No Android device/emulator connected. Start an AVD first."
}

$testCases = @(
    @{ Class = "com.tonezen.app.e2e.AppMusicDownloadE2ETest"; Method = "singleTrackDownload_visibleInMusicTab" },
    @{ Class = "com.tonezen.app.e2e.AppAuthFlowE2ETest"; Method = "coldStart_showsLoginForm" },
    @{ Class = "com.tonezen.app.e2e.AppAuthFlowE2ETest"; Method = "invalidCredentials_showsLoginError" },
    @{ Class = "com.tonezen.app.e2e.AppNavigationE2ETest"; Method = "authenticatedUser_navigatesLibraryTabsAndProfile" },
    @{ Class = "com.tonezen.app.e2e.AppNavigationE2ETest"; Method = "app_reopensFromLauncher_afterPressHome" },
    @{ Class = "com.tonezen.app.playback.TrackDownloadQueueDeviceTest"; Method = "singleTrackDownload_completesOnce_andDoesNotRestart" },
    @{ Class = "com.tonezen.app.playback.TrackDownloadQueueDeviceTest"; Method = "playbackAwaitTrack_completesWithoutRedownloadWhenFileOnDisk" },
    @{ Class = "com.tonezen.app.playback.TrackDownloadQueueDeviceTest"; Method = "bulkDownload_completesAllTracks_andStopsWorker" },
    @{ Class = "com.tonezen.app.playback.TrackDownloadQueueDeviceTest"; Method = "duplicateTrackIdAcrossBookIds_downloadsOnce" }
)

if ($OnlyTests -and $OnlyTests.Count -gt 0) {
    $testCases = $testCases | Where-Object { $OnlyTests -contains $_.Method }
}

$outputDir = Join-Path $androidRoot "app\build\e2e-recordings"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

function Stop-ScreenRecord {
    adb -s $device shell "pkill -l 2 screenrecord" 2>$null | Out-Null
    adb -s $device shell "pkill screenrecord" 2>$null | Out-Null
    Start-Sleep -Seconds 1
}

foreach ($case in $testCases) {
    $class = $case.Class
    $test = $case.Method
    $fileBase = ($class.Split(".")[-1]) + "_" + $test
    $remote = "/sdcard/e2e-$fileBase.mp4"
    $local = Join-Path $outputDir "$fileBase.mp4"
    Write-Host "`n=== Recording: $class#$test ===" -ForegroundColor Cyan

    adb -s $device shell input keyevent KEYCODE_WAKEUP 2>$null | Out-Null
    adb -s $device shell wm dismiss-keyguard 2>$null | Out-Null

    adb -s $device shell rm -f $remote 2>$null | Out-Null
    Stop-ScreenRecord

    $recordProc = Start-Process -FilePath "adb" -ArgumentList @(
        "-s", $device, "shell", "screenrecord", "--bit-rate", "6000000", "--time-limit", "180", $remote
    ) -PassThru -WindowStyle Hidden

    Start-Sleep -Seconds 2

    $gradleArgs = @(
        "connectedDebugAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=$class#$test",
        "--no-configuration-cache"
    )
    & .\gradlew @gradleArgs
    $exitCode = $LASTEXITCODE

    Stop-ScreenRecord
    if ($recordProc -and -not $recordProc.HasExited) {
        $recordProc.Kill($true)
    }

    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    & adb -s $device pull $remote $local 2>&1 | Out-Null
    $ErrorActionPreference = $prevEap
    if (Test-Path $local) {
        $size = (Get-Item $local).Length
        Write-Host "Saved: $local ($size bytes)" -ForegroundColor Green
    } else {
        Write-Warning "Video not pulled for $test"
    }

    if ($exitCode -ne 0) {
        Write-Error "Test failed: $class#$test (exit $exitCode)"
    }
}

Write-Host "`nAll recordings in: $outputDir" -ForegroundColor Green
