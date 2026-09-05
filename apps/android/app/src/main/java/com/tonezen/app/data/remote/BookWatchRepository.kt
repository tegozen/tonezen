package com.tonezen.app.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tonezen.app.MainActivity
import com.tonezen.app.data.local.BookWatchDao
import com.tonezen.app.data.local.BookWatchEntity
import com.tonezen.app.data.local.BookWatchEventEntity
import com.tonezen.app.data.local.toDomain
import com.tonezen.app.data.remote.bookwatch.BookWatchRemoteApi
import com.tonezen.app.domain.model.BookWatch
import com.tonezen.app.domain.model.BookWatchEvent
import com.tonezen.app.domain.model.BookWatchQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class BookWatchRepository @Inject constructor(
    private val dao: BookWatchDao,
    private val api: BookWatchRemoteApi,
    private val sessionRepository: SessionRepository,
    @ApplicationContext private val context: Context,
) {
    val events: Flow<List<BookWatchEvent>> = dao.observeEvents().combine(sessionRepository.session) { rows, session ->
        rows.filter { it.userId == session?.userId }.map { it.toDomain() }
    }
    val watches: Flow<List<BookWatch>> = dao.observeWatches().combine(sessionRepository.session) { rows, session ->
        rows.filter { it.userId == session?.userId }.map { it.toDomain() }
    }

    suspend fun checkOnLaunch() {
        val session = sessionRepository.loadSession() ?: return
        repeat(4) { attempt ->
            if (attempt > 0) delay((attempt + 1) * 2_000L)
            if (runCatching { sync(session.accessToken) }.isSuccess) {
                runCatching { api.enqueue(session.accessToken) }
                return
            }
        }
    }

    suspend fun sync() {
        val token = sessionRepository.loadSession()?.accessToken ?: return
        sync(token)
    }

    suspend fun resolveWatch(cycleId: String): BookWatch {
        watches.first().firstOrNull { it.cycleId == cycleId }?.let { return it }
        sync()
        return watches.first().firstOrNull { it.cycleId == cycleId }
            ?: error("Book watch configuration is missing")
    }

    suspend fun sync(token: String) {
        val snapshot = api.snapshot(token)
        val userId = sessionRepository.loadSession()?.userId ?: return
        val watches = snapshot.watches.map { json ->
            BookWatchEntity(
                id = json.getString("id"), userId = userId,
                cycleId = json.getString("cycle_id"),
                displayTitle = json.getString("display_title"), enabled = json.optBoolean("enabled", true),
                lastSuccessAt = BookWatchRemoteApi.epoch(json.optString("last_success_at").takeIf { it.isNotBlank() }),
                queriesJson = json.optJSONArray("queries")?.toString() ?: "[]",
            )
        }
        val events = snapshot.events.map { json ->
            BookWatchEventEntity(
                id = json.getString("id"), userId = userId,
                watchId = json.getString("watch_id"), kind = json.getString("kind"),
                title = json.getString("title"), author = json.optString("author").takeIf { it.isNotBlank() },
                bookNumber = json.optInt("book_number", -1).takeIf { it >= 0 }, status = json.getString("status"),
                readAt = BookWatchRemoteApi.epoch(json.optString("read_at").takeIf { it.isNotBlank() }),
                firstSeenAt = BookWatchRemoteApi.epoch(json.getString("first_seen_at")) ?: 0L,
                occurrenceCount = json.optInt("occurrence_count", 1),
                linksJson = json.optJSONArray("links")?.toString() ?: "[]",
            )
        }
        dao.upsertWatches(watches)
        dao.upsertEvents(events)
        notifyNew(events.filter { it.readAt == null })
    }

    suspend fun markRead(ids: List<String>) {
        if (ids.isEmpty()) return
        dao.markRead(ids, System.currentTimeMillis())
        sessionRepository.loadSession()?.let { runCatching { api.markRead(it.accessToken, ids) } }
    }

    suspend fun updateWatch(watch: BookWatch, displayTitle: String, queries: List<BookWatchQuery>) {
        val watchId = watch.id.ifBlank { resolveWatch(watch.cycleId).id }
        val queriesJson = JSONArray().apply {
            queries.forEach { query ->
                put(
                    JSONObject()
                        .put("provider", query.provider)
                        .put("query", query.query)
                        .put("enabled", query.enabled),
                )
            }
        }
        val body = JSONObject().put("display_title", displayTitle).put("enabled", watch.enabled).put("queries", queriesJson)
        val token = sessionRepository.loadSession()?.accessToken ?: return
        api.update(token, watchId, body)
        sync(token)
    }

    private fun notifyNew(events: List<BookWatchEventEntity>) {
        val prefs = context.getSharedPreferences("book_watch_notifications", Context.MODE_PRIVATE)
        val fresh = events.filterNot { prefs.getBoolean(it.id, false) }
        if (fresh.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Новые аудиокниги", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra("open_book_watch", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (fresh.size == 1) fresh.first().title else "Новых событий: ${fresh.size}"
        runCatching {
            manager.notify(7201, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_more).setContentTitle("Tonezen — новые книги")
                .setContentText(title).setContentIntent(intent).setAutoCancel(true).build())
        }
        prefs.edit().also { editor -> fresh.forEach { editor.putBoolean(it.id, true) } }.apply()
    }

    companion object { private const val CHANNEL = "book_watch" }
}
