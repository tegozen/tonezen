package com.tonezen.app.e2e

import android.graphics.Bitmap
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saves per-step PNG screenshots for E2E tests.
 * Host pull: apps/android/scripts/pull-e2e-screenshots.ps1
 */
object E2EScreenshots {
    private const val REPORT_PREFIX = "screenshot_"
    private const val SETTLE_MS = 400L
    private const val CAPTURE_RETRIES = 3

    private var className: String = "Unknown"
    private var methodName: String = "unknown"
    private val stepCounter = AtomicInteger(0)

    fun start(testClass: Class<*>, testMethod: String) {
        className = testClass.simpleName
        methodName = testMethod
        stepCounter.set(0)
    }

    fun capture(stepName: String): File {
        Thread.sleep(SETTLE_MS)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = captureBitmapWithRetry(instrumentation)
            ?: error("takeScreenshot() returned null for step=$stepName")

        val file = deviceFile(stepName)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }

        reportToInstrumentation(file, stepName)
        mirrorForAdbPull(file)
        return file
    }

    /** Copies to /data/local/tmp so adb pull works after instrumentation (app external dir may be cleared). */
    private fun mirrorForAdbPull(file: File) {
        val remoteDir = "/data/local/tmp/TonezenE2E/${file.parentFile?.name ?: "misc"}"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("mkdir -p $remoteDir").close()
        instrumentation.uiAutomation.executeShellCommand(
            "cp \"${file.absolutePath}\" \"$remoteDir/${file.name}\"",
        ).close()
    }

    fun deviceRoot(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "TonezenE2E")
    }

    private fun captureBitmapWithRetry(
        instrumentation: android.app.Instrumentation,
    ): Bitmap? {
        repeat(CAPTURE_RETRIES) { attempt ->
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            if (bitmap != null) return bitmap
            Thread.sleep(200L * (attempt + 1))
        }
        return null
    }

    private fun deviceFile(stepName: String): File {
        val index = stepCounter.incrementAndGet()
        val safeStep = stepName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = "%02d_%s.png".format(index, safeStep)
        return File(deviceRoot(), "$className/$methodName/$fileName")
    }

    private fun reportToInstrumentation(file: File, stepName: String) {
        val bundle = Bundle().apply {
            putString("step", stepName)
            putString("path", file.absolutePath)
            putString("report", "$REPORT_PREFIX$className#$methodName#$stepName")
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, bundle)
    }
}
