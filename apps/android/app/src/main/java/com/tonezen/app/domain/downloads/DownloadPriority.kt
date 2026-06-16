package com.tonezen.app.domain.downloads

enum class DownloadPriority(val weight: Int) {
    PREFETCH(1),
    BULK(2),
    USER(3),
    PLAY(4),
    ;

    fun higherThan(other: DownloadPriority): Boolean = weight > other.weight
}
