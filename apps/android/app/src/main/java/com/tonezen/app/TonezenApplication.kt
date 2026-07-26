package com.tonezen.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid

@HiltAndroidApp
class TonezenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val dsn = BuildConfig.GLITCHTIP_DSN.trim()
        if (dsn.isEmpty()) return

        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.isSendDefaultPii = false
            options.environment = if (BuildConfig.DEBUG) "debug" else "release"
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
        }
    }
}
