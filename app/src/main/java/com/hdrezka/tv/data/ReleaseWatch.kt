package com.hdrezka.tv.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hdrezka.tv.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

data class ReleaseWatchEntry(
    val movieId: String,
    val title: String,
    val pageUrl: String,
    val posterUrl: String?,
    val year: String?,
    val addedAt: Long
)

/** Тайтлы из «В ожидании», добавленные в избранное. */
object ReleaseWatchStore {
    private const val PREFS = "hdrezka_release_watch"

    fun list(context: Context): List<ReleaseWatchEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("items", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val result = ArrayList<ReleaseWatchEntry>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val entry = runCatching {
                ReleaseWatchEntry(
                    movieId = row.getString("movieId"),
                    title = row.getString("title"),
                    pageUrl = row.getString("pageUrl"),
                    posterUrl = row.optString("posterUrl").takeIf { it.isNotBlank() },
                    year = row.optString("year").takeIf { it.isNotBlank() },
                    addedAt = row.optLong("addedAt")
                )
            }.getOrNull() ?: continue
            result.add(entry)
        }
        return result
    }

    fun contains(context: Context, movieId: String): Boolean =
        list(context).any { it.movieId == movieId }

    fun add(context: Context, item: MovieItem) {
        val entries = list(context).filterNot { it.movieId == item.id }.toMutableList()
        entries.add(
            0,
            ReleaseWatchEntry(
                movieId = item.id,
                title = item.title,
                pageUrl = item.pageUrl,
                posterUrl = item.posterUrl,
                year = item.year,
                addedAt = System.currentTimeMillis()
            )
        )
        persist(context, entries)
    }

    fun remove(context: Context, movieId: String) {
        persist(context, list(context).filterNot { it.movieId == movieId })
    }

    fun replace(context: Context, entries: List<ReleaseWatchEntry>) {
        persist(context, entries)
    }

    fun clear(context: Context) {
        persist(context, emptyList())
    }

    fun rewriteHost(context: Context, base: String) {
        var changed = false
        val updated = list(context).map { entry ->
            val localized = SiteUrl.localize(entry.pageUrl, base)
            if (localized != entry.pageUrl) {
                changed = true
                entry.copy(pageUrl = localized)
            } else {
                entry
            }
        }
        if (changed) persist(context, updated)
    }

    private fun persist(context: Context, entries: List<ReleaseWatchEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("movieId", entry.movieId)
                    .put("title", entry.title)
                    .put("pageUrl", entry.pageUrl)
                    .put("posterUrl", entry.posterUrl ?: "")
                    .put("year", entry.year ?: "")
                    .put("addedAt", entry.addedAt)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("items", array.toString())
            .apply()
    }
}

object ReleaseWatch {
    private const val CHANNEL = "hdrezka_releases"

    suspend fun checkAndNotify(context: Context): Int = withContext(Dispatchers.IO) {
        val entries = ReleaseWatchStore.list(context)
        if (entries.isEmpty()) return@withContext 0
        val remaining = ArrayList<ReleaseWatchEntry>()
        var notified = 0
        for (entry in entries) {
            val released = runCatching { isReleased(entry) }.getOrDefault(false)
            if (released) {
                notifyRelease(context, entry)
                notified++
            } else {
                remaining.add(entry)
            }
        }
        ReleaseWatchStore.replace(context, remaining)
        notified
    }

    private suspend fun isReleased(entry: ReleaseWatchEntry): Boolean {
        val html = HdRezkaClient.get(entry.pageUrl)
        val embedded = Parser.embeddedStreams(html)
        val details = Parser.parseDetails(html, entry.pageUrl, HdRezkaClient.baseUrl)
        val result = embedded != null ||
            details.qualities.isNotEmpty() ||
            details.episodesBySeason.isNotEmpty()
        return result
    }

    fun notifyRelease(context: Context, entry: ReleaseWatchEntry) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            context,
            entry.movieId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Премьера уже доступна")
            .setContentText("«${entry.title}» вышел — можно смотреть")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("«${entry.title}» уже вышел. Откройте приложение, чтобы посмотреть.")
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(entry.movieId.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val channel = NotificationChannel(
            CHANNEL,
            "Премьеры из «В ожидании»",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }
}

object ReleaseCheckScheduler {
    private const val WORK_NAME = "hdrezka_release_check"
    private const val INTERVAL_HOURS = 6L

    fun scheduleNext(context: Context, delayMs: Long = INTERVAL_HOURS * 60L * 60L * 1000L) {
        if (ReleaseWatchStore.list(context).isEmpty()) return
        val request = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(
            INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class ReleaseCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        ReleaseWatch.checkAndNotify(applicationContext)
        return Result.success()
    }
}
