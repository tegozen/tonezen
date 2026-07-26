package com.tonezen.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tonezen.app.MainActivity
import com.tonezen.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TrackDownloadService : Service() {
    @Inject lateinit var notifier: DownloadQueueNotifier
    @Inject lateinit var queueController: TrackDownloadQueueController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val snapshot = notifier.snapshot()
        startForeground(NOTIFICATION_ID, buildNotification(snapshot))
        scope.launch {
            notifier.state.collectLatest { state ->
                if (!shouldKeepDownloadServiceForeground(state)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collectLatest
                }
                startForeground(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            queueController.cancelAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val snapshot = notifier.snapshot()
        startForeground(NOTIFICATION_ID, buildNotification(snapshot))
        if (!shouldKeepDownloadServiceForeground(snapshot)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: DownloadQueueState): Notification {
        val pending = state.queuedItems.size
        val activeTitle = state.queuedItems.find { it.trackId == state.activeTrackId }?.title
        val text = when {
            state.pausedForNetwork -> "Ожидание сети"
            activeTitle != null -> "Скачивается: ${activeTitle}"
            pending > 0 -> "В очереди: ${pending}"
            else -> "Загрузка Tonezen"
        }
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TrackDownloadService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Загрузка Tonezen")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((state.activeProgress ?: 0f) * 100).toInt(), state.activeProgress == null)
            .addAction(0, "Остановить", stopIntent)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Загрузки",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "tonezen_downloads"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "com.tonezen.app.download.STOP"
    }
}

internal fun shouldKeepDownloadServiceForeground(state: DownloadQueueState): Boolean =
    state.isActive || state.isBulkDownloading
