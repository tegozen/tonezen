package com.tonezen.app.data.local

import com.tonezen.app.domain.model.BookWatch
import com.tonezen.app.domain.model.BookWatchEvent
import com.tonezen.app.domain.model.BookWatchLink
import com.tonezen.app.domain.model.BookWatchQuery
import org.json.JSONArray

fun BookWatchEntity.toDomain() = BookWatch(
    id = id,
    cycleId = cycleId,
    displayTitle = displayTitle,
    enabled = enabled,
    lastSuccessAt = lastSuccessAt,
    queries = queriesJson.toBookWatchQueries(),
)

private fun String.toBookWatchQueries(): List<BookWatchQuery> = runCatching {
    val json = JSONArray(this)
    buildList {
        for (index in 0 until json.length()) {
            val item = json.getJSONObject(index)
            add(
                BookWatchQuery(
                    provider = item.optString("provider"),
                    query = item.optString("query"),
                    enabled = item.optBoolean("enabled", true),
                ),
            )
        }
    }
}.getOrDefault(emptyList())

fun BookWatchEventEntity.toDomain() = BookWatchEvent(
    id = id,
    watchId = watchId,
    kind = kind,
    title = title,
    author = author,
    bookNumber = bookNumber,
    status = status,
    readAt = readAt,
    firstSeenAt = firstSeenAt,
    occurrenceCount = occurrenceCount,
    links = linksJson.toBookWatchLinks(),
)

private fun String.toBookWatchLinks(): List<BookWatchLink> = runCatching {
    val json = JSONArray(this)
    buildList {
        for (index in 0 until json.length()) {
            val item = json.getJSONObject(index)
            add(BookWatchLink(provider = item.getString("provider"), url = item.getString("url")))
        }
    }
}.getOrDefault(emptyList())
