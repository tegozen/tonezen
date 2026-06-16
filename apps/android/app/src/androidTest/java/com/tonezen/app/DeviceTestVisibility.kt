package com.tonezen.app

import android.content.Intent
import android.widget.Toast
import androidx.test.platform.app.InstrumentationRegistry

object DeviceTestVisibility {
    fun launchApp(testLabel: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        instrumentation.runOnMainSync {
            context.startActivity(intent)
            Toast.makeText(context, "E2E: $testLabel", Toast.LENGTH_LONG).show()
        }
        Thread.sleep(1_500)
    }
}
