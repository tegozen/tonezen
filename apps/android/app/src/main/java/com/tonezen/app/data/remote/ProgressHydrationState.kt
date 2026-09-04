package com.tonezen.app.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ProgressHydrationState @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Volatile
    var isServerHydrated: Boolean = false
        private set

    @Volatile
    var shouldPreferRemote: Boolean = false
        private set

    fun bindUser(userId: String) {
        isServerHydrated = preferences.getString(KEY_HYDRATED_USER, null) == userId
    }

    fun prepareFromLocalCache(hasLocalProgress: Boolean) {
        if (!isServerHydrated) shouldPreferRemote = !hasLocalProgress
    }

    fun markHydrated(userId: String) {
        isServerHydrated = true
        shouldPreferRemote = false
        preferences.edit().putString(KEY_HYDRATED_USER, userId).apply()
    }

    fun clear() {
        isServerHydrated = false
        shouldPreferRemote = false
        preferences.edit().remove(KEY_HYDRATED_USER).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "tonezen_progress_sync"
        const val KEY_HYDRATED_USER = "progress_hydrated_user_id"
    }
}
