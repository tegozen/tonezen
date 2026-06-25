package com.tonezen.app.domain.music

fun <T> resolveMusicWaveDisplayTrack(
    tracks: List<T>,
    activeTrackId: String?,
    isMusicActive: Boolean,
    trackIdOf: (T) -> String,
): T? {
    if (tracks.isEmpty()) return null
    if (isMusicActive && activeTrackId != null) {
        return tracks.find { trackIdOf(it) == activeTrackId } ?: tracks.first()
    }
    return tracks.first()
}
