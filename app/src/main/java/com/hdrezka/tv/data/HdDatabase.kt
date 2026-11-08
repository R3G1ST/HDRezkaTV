package com.hdrezka.tv.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import org.json.JSONArray

@Entity(tableName = "progress")
data class ProgressEntity(
    @PrimaryKey val key: String,
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
    val seasonNumber: String?,
    val episodeNumber: String?
)

@Entity(tableName = "library", primaryKeys = ["bucket", "id"])
data class LibraryEntity(
    val bucket: String,
    val id: String,
    val title: String,
    val originalTitle: String?,
    val year: String?,
    val pageUrl: String,
    val posterUrl: String?,
    val kind: String?,
    val addedAt: Long
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC")
    fun all(): List<ProgressEntity>

    @Query("SELECT COUNT(*) FROM progress")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<ProgressEntity>)

    @Query("DELETE FROM progress WHERE `key` = :key")
    fun delete(key: String)

    @Query("DELETE FROM progress")
    fun clear()
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library WHERE bucket = :bucket ORDER BY addedAt DESC")
    fun bucket(bucket: String): List<LibraryEntity>

    @Query("SELECT COUNT(*) FROM library")
    fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: LibraryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<LibraryEntity>)

    @Query("DELETE FROM library WHERE bucket = :bucket AND id = :id")
    fun delete(bucket: String, id: String)

    @Query("DELETE FROM library WHERE bucket = :bucket")
    fun clearBucket(bucket: String)
}

@Database(
    entities = [ProgressEntity::class, LibraryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HdDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var instance: HdDatabase? = null

        fun get(context: Context): HdDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HdDatabase::class.java,
                    "hdrezka.db"
                )
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

/** Одноразовый перенос старых JSON-хранилищ в Room. */
object RoomStores {
    private const val PREFS = "hdrezka_room_migration"
    private const val DONE = "done_v1"

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val db = HdDatabase.get(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(DONE, false)) return
        runCatching {
            if (db.progressDao().count() == 0) migrateProgress(context, db)
            if (db.libraryDao().count() == 0) migrateLibrary(context, db)
            prefs.edit().putBoolean(DONE, true).apply()
        }
    }

    private fun migrateProgress(context: Context, db: HdDatabase) {
        val raw = context.getSharedPreferences("hdrezka_progress", Context.MODE_PRIVATE)
            .getString("entries", null) ?: return
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
        val entries = ArrayList<ProgressEntity>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val key = runCatching { row.getString("key") }.getOrNull() ?: continue
            entries.add(
                ProgressEntity(
                    key = key,
                    movieId = row.optString("movieId"),
                    title = row.optString("title"),
                    posterUrl = row.optString("posterUrl").takeIf { it.isNotBlank() },
                    pageUrl = row.optString("pageUrl"),
                    voiceId = row.optString("voiceId", "0"),
                    seasonId = row.optString("seasonId").takeIf { it.isNotBlank() },
                    episodeId = row.optString("episodeId").takeIf { it.isNotBlank() },
                    positionMs = row.optLong("positionMs"),
                    durationMs = row.optLong("durationMs"),
                    updatedAt = row.optLong("updatedAt"),
                    seasonNumber = row.optString("seasonNumber").takeIf { it.isNotBlank() },
                    episodeNumber = row.optString("episodeNumber").takeIf { it.isNotBlank() }
                )
            )
        }
        if (entries.isNotEmpty()) db.progressDao().upsertAll(entries)
    }

    private fun migrateLibrary(context: Context, db: HdDatabase) {
        val prefs = context.getSharedPreferences("hdrezka_library", Context.MODE_PRIVATE)
        for (bucket in listOf("favorites", "watchLater")) {
            val raw = prefs.getString(bucket, null) ?: continue
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: continue
            val entries = ArrayList<LibraryEntity>()
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = runCatching { row.getString("id") }.getOrNull() ?: continue
                entries.add(
                    LibraryEntity(
                        bucket = bucket,
                        id = id,
                        title = row.optString("title"),
                        originalTitle = row.optString("originalTitle").takeIf { it.isNotBlank() },
                        year = row.optString("year").takeIf { it.isNotBlank() },
                        pageUrl = row.optString("pageUrl"),
                        posterUrl = row.optString("posterUrl").takeIf { it.isNotBlank() },
                        kind = row.optString("kind").takeIf { it.isNotBlank() },
                        addedAt = row.optLong("addedAt")
                    )
                )
            }
            if (entries.isNotEmpty()) db.libraryDao().upsertAll(entries)
        }
    }
}
