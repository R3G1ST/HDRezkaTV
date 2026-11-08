package com.hdrezka.tv.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GithubSyncConfig(
    val owner: String,
    val repo: String,
    val token: String
) {
    val isReady: Boolean
        get() = owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()
}

data class SyncSummary(
    val remoteCount: Int,
    val mergedCount: Int,
    val at: Long
)

data class PullSummary(
    val remoteCount: Int,
    val mergedCount: Int,
    val changed: Boolean,
    val at: Long
)

object GithubSyncConfigStore {
    private const val PREFS = "github_sync"

    fun load(context: Context): GithubSyncConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val owner = prefs.getString("owner", "").orEmpty().trim()
        val repo = prefs.getString("repo", "").orEmpty().trim()
        val token = prefs.getString("token", "").orEmpty().trim()
        if (owner.isBlank() || repo.isBlank() || token.isBlank()) return null
        return GithubSyncConfig(owner, repo, token)
    }

    fun save(context: Context, config: GithubSyncConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("owner", config.owner.trim())
            .putString("repo", config.repo.trim())
            .putString("token", config.token.trim())
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun lastSync(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong("lastSync", 0L)

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("lastError", null)
            ?.takeIf { it.isNotBlank() }

    fun setStatus(context: Context, at: Long, error: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong("lastSync", at)
            .putString("lastError", error)
            .apply()
    }
}

/**
 * Синхронизация точных позиций просмотра через приватный репозиторий GitHub.
 * Файл: progress.json в основной ветке репозитория.
 */
object GithubProgressSync {
    private const val FILE_PATH = "progress.json"
    private val syncMutex = Mutex()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun sync(context: Context): Result<SyncSummary> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
        val config = GithubSyncConfigStore.load(context)
            ?: return@withContext Result.failure(
                IllegalStateException("GitHub-синхронизация не настроена")
            )
        runCatching {
            var conflict = false
            repeat(5) {
                val file = fetchFile(config)
                val remote = parse(file.first)
                val merged = merge(ProgressStore.list(context), remote)
                ProgressStore.replaceAll(context, merged)
                if (putFile(config, serialize(merged), file.second)) {
                    val at = System.currentTimeMillis()
                    GithubSyncConfigStore.setStatus(context, at, null)
                    return@runCatching SyncSummary(remote.size, merged.size, at)
                }
                conflict = true
                Thread.sleep(250L + Random.nextLong(0L, 500L))
            }
            throw IllegalStateException(
                if (conflict) "Файл изменился во время записи, повторите синхронизацию"
                else "Не удалось записать progress.json"
            )
        }.onFailure {
            GithubSyncConfigStore.setStatus(
                context,
                System.currentTimeMillis(),
                it.message ?: it.javaClass.simpleName
            )
        }
        }
    }

    /**
     * Только получить свежие позиции с GitHub (без записи).
     * Нужно для частого опроса: не создаёт коммит на каждый тик.
     */
    suspend fun pull(context: Context): Result<PullSummary> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val config = GithubSyncConfigStore.load(context)
                ?: return@withContext Result.failure(
                    IllegalStateException("GitHub-синхронизация не настроена")
                )
            runCatching {
                val file = fetchFile(config)
                val remote = parse(file.first)
                val local = ProgressStore.list(context)
                val merged = merge(local, remote)
                val changed = merged != local
                if (changed) ProgressStore.replaceAll(context, merged)
                val at = System.currentTimeMillis()
                GithubSyncConfigStore.setStatus(context, at, null)
                PullSummary(remote.size, merged.size, changed, at)
            }.onFailure {
                GithubSyncConfigStore.setStatus(
                    context,
                    System.currentTimeMillis(),
                    it.message ?: it.javaClass.simpleName
                )
            }
        }
    }

    private fun fetchFile(config: GithubSyncConfig): Pair<String?, String?> {
        val url = "https://api.github.com/repos/${config.owner}/${config.repo}/contents/$FILE_PATH"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HDRezkaTV")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                Pair(null, null)
            } else {
                if (!response.isSuccessful) {
                    error("GitHub GET ${response.code}: ${response.body?.string()?.take(220)}")
                }
                val json = JSONObject(response.body?.string().orEmpty())
                val encoded = json.optString("content")
                val content = if (encoded.isBlank()) null else {
                    String(
                        Base64.decode(encoded.replace("\n", "").replace("\r", ""), Base64.DEFAULT),
                        Charsets.UTF_8
                    )
                }
                Pair(content, json.optString("sha").takeIf { it.isNotBlank() })
            }
        }
    }

    private fun putFile(config: GithubSyncConfig, content: String, sha: String?): Boolean {
        val url = "https://api.github.com/repos/${config.owner}/${config.repo}/contents/$FILE_PATH"
        val payload = JSONObject()
            .put("message", "Sync HDrezka watch progress")
            .put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
        if (!sha.isNullOrBlank()) payload.put("sha", sha)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "HDRezkaTV")
            .put(payload.toString().toRequestBody(jsonType))
            .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.code == 409 || response.code == 422 -> false
                response.isSuccessful -> true
                else -> error("GitHub PUT ${response.code}: ${response.body?.string()?.take(220)}")
            }
        }
    }

    private fun localizePageUrl(raw: String): String {
        if (raw.isBlank()) return raw
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return raw
        val path = uri.rawPath
        if (path.isNullOrBlank()) return raw
        val base = HdRezkaClient.baseUrl.trimEnd('/')
        val query = uri.rawQuery
        return buildString {
            append(base)
            append(path)
            if (!query.isNullOrBlank()) append('?').append(query)
        }
    }

    private fun normalizeEntry(entry: ProgressEntry): ProgressEntry {
        val localized = localizePageUrl(entry.pageUrl)
        return if (localized == entry.pageUrl) entry else entry.copy(pageUrl = localized)
    }

    private fun canonical(entry: ProgressEntry): String =
        "${entry.movieId}|${entry.seasonId.orEmpty()}|${entry.episodeId.orEmpty()}"

    private fun merge(
        local: List<ProgressEntry>,
        remote: List<ProgressEntry>
    ): List<ProgressEntry> {
        val byKey = LinkedHashMap<String, ProgressEntry>()
        for (entry in local) {
            val normalized = normalizeEntry(entry)
            byKey[canonical(normalized)] = normalized
        }
        for (entry in remote) {
            val normalized = normalizeEntry(entry)
            val key = canonical(normalized)
            val existing = byKey[key]
            if (existing == null || normalized.updatedAt > existing.updatedAt) {
                byKey[key] = normalized
            }
        }
        return byKey.values.sortedByDescending { it.updatedAt }.take(200)
    }

    private fun parse(raw: String?): List<ProgressEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("entries") ?: return emptyList()
        val result = ArrayList<ProgressEntry>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val movieId = row.optString("movieId").takeIf { it.isNotBlank() } ?: continue
            val seasonId = row.optString("seasonId").takeIf { it.isNotBlank() }
            val episodeId = row.optString("episodeId").takeIf { it.isNotBlank() }
            // На macOS сезон/серия часто хранятся номерами ("3", "7"),
            // а не внутренними ID сайта. Для выбора серии нужны оба варианта.
            fun looksLikeNumber(value: String?): Boolean =
                value != null && value.length <= 2 && value.all { it.isDigit() }
            val seasonNumber = row.optString("seasonNumber").takeIf { it.isNotBlank() }
                ?: seasonId?.takeIf(::looksLikeNumber)
            val episodeNumber = row.optString("episodeNumber").takeIf { it.isNotBlank() }
                ?: episodeId?.takeIf(::looksLikeNumber)
            val position = row.optDouble("position", 0.0)
            val duration = row.optDouble("duration", 0.0)
            if (position <= 0.0) continue
            val pagePath = row.optString("pagePath")
            val pageUrl = if (pagePath.isNotBlank()) {
                HdRezkaClient.baseUrl.trimEnd('/') + pagePath
            } else {
                localizePageUrl(row.optString("pageUrl"))
            }
            result.add(
                ProgressEntry(
                    key = ProgressStore.key(movieId, seasonId, episodeId),
                    movieId = movieId,
                    title = row.optString("title"),
                    posterUrl = row.optString("posterUrl").takeIf { it.isNotBlank() },
                    pageUrl = pageUrl,
                    voiceId = row.optString("voiceId", "0"),
                    seasonId = seasonId,
                    episodeId = episodeId,
                    positionMs = (position * 1000.0).toLong(),
                    durationMs = (duration * 1000.0).toLong(),
                    updatedAt = row.optLong("updatedAt", 0L),
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber
                )
            )
        }
        return result
    }

    private fun serialize(entries: List<ProgressEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("movieId", entry.movieId)
                    .put("seasonId", entry.seasonId ?: "")
                    .put("episodeId", entry.episodeId ?: "")
                    .put("title", entry.title)
                    .put("posterUrl", entry.posterUrl ?: "")
                    .put("pageUrl", entry.pageUrl)
                    .put(
                        "pagePath",
                        runCatching { java.net.URI(entry.pageUrl).rawPath }
                            .getOrNull()
                            ?: ""
                    )
                    .put("voiceId", entry.voiceId)
                    .put("position", entry.positionMs / 1000.0)
                    .put("duration", entry.durationMs / 1000.0)
                    .put("updatedAt", entry.updatedAt)
                    .put("seasonNumber", entry.seasonNumber ?: "")
                    .put("episodeNumber", entry.episodeNumber ?: "")
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("updatedAt", System.currentTimeMillis())
            .put("entries", array)
            .toString()
    }
}
