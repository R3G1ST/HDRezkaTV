package com.hdrezka.tv.data

import android.content.Context

data class SavedItem(
    val item: MovieItem,
    val addedAt: Long
)

object LibraryStore {
    private const val FAVORITES = "favorites"
    private const val WATCH_LATER = "watchLater"

    private fun dao(context: Context) = HdDatabase.get(context).libraryDao()

    fun favorites(context: Context): List<SavedItem> =
        dao(context).bucket(FAVORITES).map { it.toSavedItem() }

    fun watchLater(context: Context): List<SavedItem> =
        dao(context).bucket(WATCH_LATER).map { it.toSavedItem() }

    fun rewriteHost(context: Context, base: String) {
        for (bucket in listOf(FAVORITES, WATCH_LATER)) {
            val items = dao(context).bucket(bucket)
            var changed = false
            val updated = items.map { entity ->
                val localized = SiteUrl.localize(entity.pageUrl, base)
                if (localized != entity.pageUrl) {
                    changed = true
                    entity.copy(pageUrl = localized)
                } else {
                    entity
                }
            }
            if (changed) {
                dao(context).clearBucket(bucket)
                dao(context).upsertAll(updated)
            }
        }
    }

    fun clearFavorites(context: Context) {
        dao(context).clearBucket(FAVORITES)
    }

    fun clearWatchLater(context: Context) {
        dao(context).clearBucket(WATCH_LATER)
    }

    fun isFavorite(context: Context, id: String): Boolean =
        favorites(context).any { it.item.id == id }

    fun isWatchLater(context: Context, id: String): Boolean =
        watchLater(context).any { it.item.id == id }

    fun importFavorites(context: Context, items: List<MovieItem>) {
        val current = favorites(context)
        val ids = current.mapTo(HashSet()) { it.item.id }
        for (item in items) {
            if (ids.add(item.id)) {
                dao(context).upsert(item.toEntity(FAVORITES, System.currentTimeMillis()))
            }
        }
    }

    fun toggleFavorite(context: Context, item: MovieItem) {
        toggle(context, FAVORITES, item)
    }

    fun toggleWatchLater(context: Context, item: MovieItem) {
        toggle(context, WATCH_LATER, item)
    }

    private fun toggle(context: Context, bucket: String, item: MovieItem) {
        val exists = dao(context).bucket(bucket).any { it.id == item.id }
        if (exists) {
            dao(context).delete(bucket, item.id)
        } else {
            dao(context).upsert(item.toEntity(bucket, System.currentTimeMillis()))
        }
    }

    private fun LibraryEntity.toSavedItem() = SavedItem(
        item = MovieItem(
            id = id,
            title = title,
            originalTitle = originalTitle,
            year = year,
            pageUrl = pageUrl,
            posterUrl = posterUrl,
            kind = kind
        ),
        addedAt = addedAt
    )

    private fun MovieItem.toEntity(bucket: String, addedAt: Long) = LibraryEntity(
        bucket = bucket,
        id = id,
        title = title,
        originalTitle = originalTitle,
        year = year,
        pageUrl = pageUrl,
        posterUrl = posterUrl,
        kind = kind,
        addedAt = addedAt
    )
}
