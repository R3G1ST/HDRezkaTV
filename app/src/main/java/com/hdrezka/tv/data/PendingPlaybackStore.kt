package com.hdrezka.tv.data

import android.content.Context

/**
 * Сессия, запущенная во внешнем плеере (Vimu). Нужна, чтобы позиция
 * не терялась, если Vimu не вернул ActivityResult: например, пользователь
 * вышел из плеера через Home или процесс приложения был выгружен.
 */
data class PendingPlayback(
    val key: String,
    val movieId: String,
    val title: String,
    val posterUrl: String?,
    val pageUrl: String,
    val voiceId: String,
    val seasonId: String?,
    val episodeId: String?,
    val startAtMs: Long,
    val durationMs: Long,
    val launchedAt: Long
) {
    fun estimatedPosition(now: Long = System.currentTimeMillis()): Long {
        val elapsed = (now - launchedAt).coerceAtLeast(0L)
        val raw = startAtMs + elapsed
        val limit = if (durationMs > 0L) {
            durationMs
        } else {
            startAtMs + 6L * 60L * 60L * 1000L
        }
        return raw.coerceAtMost(limit)
    }
}

object PendingPlaybackStore {
    private const val PREFS = "hdrezka_pending_playback"

    fun save(context: Context, pending: PendingPlayback) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("key", pending.key)
            .putString("movieId", pending.movieId)
            .putString("title", pending.title)
            .putString("posterUrl", pending.posterUrl ?: "")
            .putString("pageUrl", pending.pageUrl)
            .putString("voiceId", pending.voiceId)
            .putString("seasonId", pending.seasonId ?: "")
            .putString("episodeId", pending.episodeId ?: "")
            .putLong("startAtMs", pending.startAtMs)
            .putLong("durationMs", pending.durationMs)
            .putLong("launchedAt", pending.launchedAt)
            .commit()
    }

    fun load(context: Context): PendingPlayback? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = prefs.getString("key", "").orEmpty()
        val movieId = prefs.getString("movieId", "").orEmpty()
        if (key.isBlank() || movieId.isBlank()) return null
        return PendingPlayback(
            key = key,
            movieId = movieId,
            title = prefs.getString("title", "").orEmpty(),
            posterUrl = prefs.getString("posterUrl", "")?.takeIf { it.isNotBlank() },
            pageUrl = prefs.getString("pageUrl", "").orEmpty(),
            voiceId = prefs.getString("voiceId", "0").orEmpty().ifBlank { "0" },
            seasonId = prefs.getString("seasonId", "")?.takeIf { it.isNotBlank() },
            episodeId = prefs.getString("episodeId", "")?.takeIf { it.isNotBlank() },
            startAtMs = prefs.getLong("startAtMs", 0L),
            durationMs = prefs.getLong("durationMs", 0L),
            launchedAt = prefs.getLong("launchedAt", 0L)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
