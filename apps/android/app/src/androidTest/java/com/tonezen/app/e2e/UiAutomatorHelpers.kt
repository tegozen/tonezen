package com.tonezen.app.e2e

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector

object UiAutomatorHelpers {
    fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun prepareDevice(device: UiDevice = device()) {
        grantRuntimePermissions()
        if (!device.isScreenOn) {
            device.wakeUp()
        }
        dismissSystemDialogs(device)
    }

    fun grantRuntimePermissions(packageName: String = "com.tonezen.app") {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add("android.permission.POST_NOTIFICATIONS")
            }
        }
        permissions.forEach { permission ->
            instrumentation.uiAutomation.executeShellCommand("pm grant $packageName $permission")
                .close()
        }
    }

    fun dismissSystemDialogs(device: UiDevice = device()) {
        val patterns = listOf(
            "(?i)allow",
            "(?i)разрешить",
            "(?i)while using the app",
            "(?i)только в этом приложении",
            "(?i)don't allow",
            "(?i)запретить",
        )
        repeat(3) {
            for (pattern in patterns) {
                val button = device.findObject(UiSelector().textMatches(pattern))
                if (button.waitForExists(500)) {
                    button.click()
                }
            }
        }
    }

    fun waitForText(text: String, timeoutMs: Long = 5_000): Boolean =
        device().findObject(UiSelector().textContains(text)).waitForExists(timeoutMs)
}
