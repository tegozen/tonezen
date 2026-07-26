package com.tonezen.app.data.nearby

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceLabelResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(): String {
        val settingsName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim().orEmpty()
        if (settingsName.isNotBlank()) return settingsName

        val btName = runCatching {
            val manager = context.getSystemService(BluetoothManager::class.java)
            val adapter: BluetoothAdapter? = manager?.adapter
            @Suppress("DEPRECATION")
            adapter?.name
        }.getOrNull()?.trim().orEmpty()
        if (btName.isNotBlank()) return btName

        val model = Build.MODEL?.trim().orEmpty()
        if (model.isNotBlank()) return model
        return "Android"
    }
}
