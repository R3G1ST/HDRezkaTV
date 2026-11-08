package com.hdrezka.tv.data

import android.content.Context

data class ProgressEntry(
    val key: String,
    val movieId: String,
    val title: String,
    val posterUrl: String?,
    val pageUrl: String,
    val voiceId: String,
    val seasonId: String?,
    val episodeId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val seasonNumber: String? = null,
    val episodeNumber: String? = null
) {
    val progress: Double
        get() = if (durationMs > 0) {
            (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        } else 0.0
}

object ProgressStore {
    private fun dao(context: Context) = HdDatabase.get(context).progressDao()

    fun key(movieId: String, seasonId: String?, episodeId: String?): String =
        listOf(movieId, seasonId ?: "", episodeId ?: "").joinToString(":")

    fun load(context: Context, key: String): Long =
        list(context).firstOrNull { it.key == key }?.positionMs ?: 0L

    fun save(
        context: Context,
        key: String,
        movieId: String,
        title: String,
        posterUrl: String?,
        pageUrl: String,
        voiceId: String,
        seasonId: String?,
        episodeId: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (positionMs <= 5_000L) return
        dao(context).upsert(
            ProgressEntity(
                key = key,
                movieId = movieId,
                title = title,
                posterUrl = posterUrl,
                pageUrl = pageUrl,
                voiceId = voiceId,
                seasonId = seasonId,
                episodeId = episodeId,
                positionMs = positionMs,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis(),
                seasonNumber = null,
                episodeNumber = null
            )
        )
    }

    fun rewriteHost(context: Context, base: String) {
        val entries = list(context)
        var changed = false
        val updated = entries.map { entry ->
            val localized = SiteUrl.localize(entry.pageUrl, base)
            if (localized != entry.pageUrl) {
                changed = true
                entry.copy(pageUrl = localized)
            } else {
                entry
            }
        }
        if (changed) replaceAll(context, updated)
    }

    fun clear(context: Context) {
        dao(context).clear()
    }

    fun replaceAll(context: Context, entries: List<ProgressEntry>) {
        val db = HdDatabase.get(context)
        db.runInTransaction {
            db.progressDao().clear()
            db.progressDao().upsertAll(
                entries.sortedByDescending { it.updatedAt }.take(200).map { it.toEntity() }
            )
        }
    }

    fun list(context: Context): List<ProgressEntry> =
        dao(context).all().map { it.toModel() }

    private fun ProgressEntity.toModel() = ProgressEntry(
        key = key,
        movieId = movieId,
        title = title,
        posterUrl = posterUrl,
        pageUrl = pageUrl,
        voiceId = voiceId,
        seasonId = seasonId,
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber
    )

    private fun ProgressEntry.toEntity() = ProgressEntity(
        key = key,
        movieId = movieId,
        title = title,
        posterUrl = posterUrl,
        pageUrl = pageUrl,
        voiceId = voiceId,
        seasonId = seasonId,
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        updatedAt = updatedAt,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber
    )
}
