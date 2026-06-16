package com.tonezen.app.e2e

import org.junit.rules.TestWatcher
import org.junit.runner.Description

class E2EScreenshotRule : TestWatcher() {
    override fun starting(description: Description) {
        E2EScreenshots.start(description.testClass, description.methodName)
    }
}
