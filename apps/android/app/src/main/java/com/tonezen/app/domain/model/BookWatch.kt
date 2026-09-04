package com.tonezen.app.domain.model

data class BookWatchEvent(
    val id: String,
    val watchId: String,
    val kind: String,
    val title: String,
    val author: String?,
    val bookNumber: Int?,
    val status: String,
    val readAt: Long?,
    val firstSeenAt: Long,
    val occurrenceCount: Int,
    val links: List<BookWatchLink>,
)

data class BookWatchLink(
    val provider: String,
    val url: String,
)

data class BookWatch(
    val id: String,
    val cycleId: String,
    val displayTitle: String,
    val enabled: Boolean,
    val lastSuccessAt: Long?,
    val queries: List<BookWatchQuery>,
)

data class BookWatchQuery(
    val provider: String,
    val query: String,
    val enabled: Boolean,
)
