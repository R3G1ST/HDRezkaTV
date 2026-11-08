package com.hdrezka.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HdRezkaException(message: String) : Exception(message)

object HdRezkaClient {
    val userAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.1 Safari/605.1.15"

    private val cookies = PersistentCookieJar()

    fun init(context: Context) {
        cookies.attach(context.applicationContext)
        Mirrors.active(context)?.let { baseUrl = it }
    }

    /** Персональная сборка: подкладываем сохранённые cookie аккаунта. */
    fun seedCookies(rawJson: String) {
        cookies.seed(rawJson)
    }

    private val http = OkHttpClient.Builder()
        .cookieJar(cookies)
        .dns(DohDns())
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    var baseUrl: String = "https://hdrezka.ag"
        private set

    fun setBaseUrl(value: String) {
        val clean = value.trim().trimEnd('/')
        if (clean.isNotEmpty()) baseUrl = clean
    }

    private val diagnosticHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(DohDns())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Проверка связи с зеркалом: возвращает код/задержку без побочных эффектов. */
    suspend fun testConnection(base: String = baseUrl): ConnectionResult =
        withContext(Dispatchers.IO) {
            val normalized = base.trim().trimEnd('/')
            val started = System.currentTimeMillis()
            runCatching {
                val request = Request.Builder()
                    .url("$normalized/")
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                    .build()
                diagnosticHttp.newCall(request).execute().use { response ->
                    val elapsed = System.currentTimeMillis() - started
                    if (!response.isSuccessful) {
                        ConnectionResult(normalized, false, "HTTP ${response.code}", elapsed)
                    } else {
                        val host = response.request.url.host
                        ConnectionResult(normalized, true, "OK · $host", elapsed)
                    }
                }
            }.getOrElse { error ->
                ConnectionResult(
                    normalized,
                    false,
                    error.message ?: "Нет связи",
                    System.currentTimeMillis() - started
                )
            }
        }

    /// Клиент для ExoPlayer: DoH + cookie сайта. Заголовки Referer/UA
    /// выставляет источник данных плеера.
    val playerHttp: OkHttpClient get() = http

    /// Отдельный клиент для постеров: Coil не знает про DoH и заголовки сайта.
    val imageHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(DohDns())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Referer", baseUrl + "/")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    suspend fun login(login: String, password: String, remember: Boolean = true): AccountInfo {
        // Прогреваем Anubis, иначе /ajax/login/ отдаст челлендж.
        runCatching { get("/") }
        val body = postForm(
            "/ajax/login/",
            mapOf(
                "login_name" to login,
                "login_password" to password,
                "login_not_save" to if (remember) "0" else "1"
            ),
            referer = absolute("/login/")
        )
        val json = JSONObject(body)
        val success = json.optBoolean("success", false) ||
            json.optString("logged_email").isNotBlank()
        if (!success) {
            val message = json.optString("message")
            throw HdRezkaException(message.ifBlank { "Неверный логин или пароль" })
        }
        return currentAccount() ?: AccountInfo(id = "0", name = login, isPremium = false)
    }

    suspend fun currentAccount(): AccountInfo? {
        val html = runCatching { get("/") }.getOrNull() ?: return null
        var account = Parser.parseAccount(html)
        val profileName = runCatching {
            Parser.parseProfileName(get("/settings/"))
        }.getOrNull()
        if (account != null && !profileName.isNullOrBlank()) {
            account = account.copy(name = profileName)
        } else if (account == null && !profileName.isNullOrBlank()) {
            account = AccountInfo(
                id = "settings",
                name = profileName,
                isPremium = false
            )
        }
        return account
    }

    suspend fun reportProgress(
        movieId: String,
        translator: String,
        season: String?,
        episode: String?,
        positionSec: Long,
        durationSec: Long
    ) {
        if (movieId.isBlank() || durationSec <= 0L) return
        val stamp = System.currentTimeMillis()
        runCatching {
            postForm(
                "/ajax/send_save/?t=$stamp",
                mapOf(
                    "post_id" to movieId,
                    "translator_id" to translator,
                    "season" to (season ?: "0"),
                    "episode" to (episode ?: "0"),
                    "current_time" to positionSec.coerceAtLeast(0).toString(),
                    "duration" to durationSec.coerceAtLeast(0).toString()
                ),
                referer = baseUrl + "/"
            )
        }
    }

    suspend fun catalog(
        section: CatalogSection,
        page: Int,
        genre: String? = null,
        year: String? = null,
        sort: CatalogSort = CatalogSort.FRESH
    ): List<MovieItem> {
        var path: String
        if (year != null) {
            path = "/${section.path}/best"
            if (!genre.isNullOrBlank()) path += "/$genre"
            path += "/$year/"
            if (page > 1) path += "page/$page/"
        } else {
            path = "/${section.path}/"
            if (!genre.isNullOrBlank()) path += "$genre/"
            if (page > 1) path += "page/$page/"
            path += "?filter=${sort.filter}"
        }
        val html = get(path)
        return Parser.parseItems(html, baseUrl)
    }

    @Volatile
    private var topCache: Pair<Long, List<MovieItem>>? = null

    /** Топ-10 с сайта (hdrezka.inc/top-100.html). */
    suspend fun top100(limit: Int = 10): List<MovieItem> {
        val cached = topCache
        if (cached != null && System.currentTimeMillis() - cached.first < 10 * 60_000L) {
            return cached.second.take(limit)
        }
        val html = get("https://hdrezka.inc/top-100.html")
        val parsed = withContext(Dispatchers.Default) {
            Parser.parseTop100(html, "https://hdrezka.inc")
        }.map { item ->
            item.copy(pageUrl = SiteUrl.localizeItem(item.pageUrl, baseUrl))
        }
        topCache = System.currentTimeMillis() to parsed
        return parsed.take(limit)
    }

    suspend fun search(query: String): List<MovieItem> {
        val html = postForm(
            "/search/?do=search&subaction=search",
            mapOf(
                "q" to query,
                "do" to "search",
                "subaction" to "search"
            ),
            referer = baseUrl + "/"
        )
        return withContext(Dispatchers.Default) {
            Parser.parseItems(html, baseUrl)
        }
    }

    private val detailsCache = ConcurrentHashMap<String, MovieDetails>()

    suspend fun details(pageUrl: String, fallbackTitle: String? = null): MovieDetails {
        detailsCache[pageUrl]?.let { return it }
        var targetUrl = pageUrl
        val html = try {
            get(pageUrl)
        } catch (error: Exception) {
            // На домашнем зеркале некоторые тайтлы из подборок стримингов
            // лежат под другим ID. Ищем по названию, прежде чем показать 404.
            val alternative = if (fallbackTitle != null && error.message?.contains("404") == true) {
                findAlternativePage(fallbackTitle, pageUrl)
            } else {
                null
            }
            if (alternative == null) throw error
            targetUrl = alternative
            get(alternative)
        }
        val parsed = withContext(Dispatchers.Default) {
            Parser.parseDetails(html, targetUrl, baseUrl)
        }
        if (detailsCache.size > 80) detailsCache.clear()
        detailsCache[pageUrl] = parsed
        if (targetUrl != pageUrl) detailsCache[targetUrl] = parsed
        return parsed
    }

    private suspend fun findAlternativePage(title: String, originalUrl: String): String? {
        val results = runCatching { search(title) }.getOrDefault(emptyList())
        if (results.isEmpty()) return null
        val wanted = normalizeTitle(title)
        if (wanted.isBlank()) return null
        val exact = results.firstOrNull {
            normalizeTitle(it.title) == wanted && it.pageUrl != originalUrl
        }
        if (exact != null) return exact.pageUrl
        return results.firstOrNull {
            val candidate = normalizeTitle(it.title)
            candidate.isNotBlank() &&
                it.pageUrl != originalUrl &&
                (candidate.startsWith(wanted) || wanted.startsWith(candidate))
        }?.pageUrl
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase().replace("ё", "е").filter { it.isLetterOrDigit() }

    suspend fun trailer(movieId: String): TrailerInfo {
        val body = postForm(
            "/engine/ajax/gettrailervideo.php",
            mapOf("id" to movieId),
            referer = baseUrl + "/"
        )
        val json = JSONObject(body)
        if (!json.optBoolean("success", true)) {
            throw HdRezkaException(json.optString("message").ifBlank { "Трейлер недоступен" })
        }
        val code = json.optString("code")
        var iframe = Regex("src=\"([^\"]+)\"").find(code)?.groupValues?.getOrNull(1)
        if (iframe != null && iframe.startsWith("//")) iframe = "https:$iframe"
        return TrailerInfo(
            title = json.optString("title"),
            iframeUrl = iframe,
            pageUrl = json.optString("link").takeIf { it.isNotBlank() }
        )
    }

    suspend fun collections(page: Int): List<Collection> {
        val path = if (page <= 1) "/collections/" else "/collections/page/$page/"
        val html = get(path)
        return withContext(Dispatchers.Default) {
            Parser.parseCollections(html, baseUrl)
        }
    }

    suspend fun collectionItems(
        collectionUrl: String,
        page: Int
    ): List<MovieItem> {
        val base = collectionUrl.trimEnd('/')
        val path = if (page <= 1) "$base/" else "$base/page/$page/"
        val html = get(path)
        return withContext(Dispatchers.Default) {
            Parser.parseItems(html, baseUrl)
        }
    }

    suspend fun personPage(url: String): Pair<String, List<MovieItem>> {
        val html = get(url)
        return withContext(Dispatchers.Default) {
            Parser.parsePersonPage(html, baseUrl)
        }
    }

    suspend fun siteContinue(): List<SiteContinueItem> {
        val html = get("/continue/")
        return withContext(Dispatchers.Default) {
            Parser.parseContinue(html, baseUrl)
        }
    }

    suspend fun siteFavorites(): List<MovieItem> {
        val html = get("/favorites/")
        return withContext(Dispatchers.Default) {
            Parser.parseFavorites(html, baseUrl)
        }
    }

    suspend fun setFavorite(movieId: String, pageUrl: String, favorite: Boolean) {
        val html = get("/favorites/")
        val category = Parser.parseFavoriteCategories(html).firstOrNull()?.first ?: return
        postForm(
            "/ajax/favorites/",
            mapOf(
                "post_id" to movieId,
                "cat_id" to category,
                "action" to "add_post"
            ),
            referer = pageUrl
        )
    }

    suspend fun logout() {
        runCatching { get("/logout/") }
        cookies.clear()
    }

    suspend fun get(pathOrUrl: String, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            val url = absolute(pathOrUrl)
            execute(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .apply { if (referer != null) header("Referer", referer) }
                    .get()
                    .build()
            )
        }

    suspend fun postForm(
        pathOrUrl: String,
        form: Map<String, String>,
        referer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply {
            form.forEach { (key, value) -> add(key, value) }
        }.build()
        execute(
            Request.Builder()
                .url(absolute(pathOrUrl))
                .header("User-Agent", userAgent)
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")
                .apply { if (referer != null) header("Referer", referer) }
                .post(body)
                .build()
        )
    }

    private fun execute(original: Request): String {
        var request = original
        repeat(3) { attempt ->
            val response = http.newCall(request).execute()
            response.use { res ->
                res.request.url.let { finalUrl ->
                    baseUrl = "${finalUrl.scheme}://${finalUrl.host}"
                }
                val html = res.body?.string().orEmpty()
                val challenge = AnubisChallenge.parse(html)
                if (challenge == null) {
                    if (!res.isSuccessful) {
                        throw HdRezkaException("HTTP ${res.code}")
                    }
                    if (html.isBlank()) throw HdRezkaException("Пустая страница")
                    return html
                }

                if (attempt == 2) throw HdRezkaException("Антибот-проверка не пройдена")
                val solved = challenge.solve()
                    ?: throw HdRezkaException("Не удалось решить Anubis")
                passChallenge(request.url, challenge, solved.first, solved.second)
                request = original.newBuilder().build()
            }
        }
        throw HdRezkaException("Не удалось загрузить страницу")
    }

    private fun passChallenge(
        originalUrl: HttpUrl,
        challenge: AnubisChallenge,
        nonce: Int,
        hash: String
    ) {
        val passUrl = (baseUrl + "/.within.website/x/cmd/anubis/api/pass-challenge")
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("id", challenge.id)
            .addQueryParameter("response", hash)
            .addQueryParameter("nonce", nonce.toString())
            .addQueryParameter("redir", originalUrl.encodedPath)
            .addQueryParameter("elapsedTime", "1500")
            .build()

        val request = Request.Builder()
            .url(passUrl)
            .header("User-Agent", userAgent)
            .header("Referer", originalUrl.toString())
            .get()
            .build()
        http.newCall(request).execute().use { /* cookie уже сохранена */ }
    }

    fun absolute(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl
        }
        val base = baseUrl.trimEnd('/')
        return if (pathOrUrl.startsWith("/")) base + pathOrUrl else "$base/$pathOrUrl"
    }

    fun absoluteUrl(path: String, base: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanBase = base.trimEnd('/')
        return if (path.startsWith("/")) cleanBase + path else "$cleanBase/$path"
    }

    /// DNS-over-HTTPS: у некоторых провайдеров домен сайта подменяется
    /// на заглушку с чужим сертификатом. DoH возвращает настоящий IP.
    private class DohDns : Dns {
        private val cache = ConcurrentHashMap<String, List<InetAddress>>()
        private val bootstrap = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        override fun lookup(hostname: String): List<InetAddress> {
            cache[hostname]?.let { return it }
            val resolved = resolve(hostname) ?: Dns.SYSTEM.lookup(hostname)
            cache[hostname] = resolved
            return resolved
        }

        private fun resolve(hostname: String): List<InetAddress>? {
            val encoded = URLEncoder.encode(hostname, "UTF-8")
            val endpoints = listOf(
                "https://dns.google/resolve?name=$encoded&type=A",
                "https://cloudflare-dns.com/dns-query?name=$encoded&type=A"
            )
            for (endpoint in endpoints) {
                val addresses = runCatching {
                    val request = Request.Builder()
                        .url(endpoint)
                        .header("Accept", "application/dns-json")
                        .build()
                    bootstrap.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use emptyList()
                        val json = JSONObject(response.body?.string().orEmpty())
                        val answer = json.optJSONArray("Answer") ?: return@use emptyList()
                        val result = ArrayList<InetAddress>()
                        for (i in 0 until answer.length()) {
                            val row = answer.optJSONObject(i) ?: continue
                            if (row.optInt("type") != 1) continue
                            val ip = row.optString("data")
                            if (ip.isNotBlank()) {
                                runCatching { InetAddress.getByName(ip) }
                                    .getOrNull()?.let { result.add(it) }
                            }
                        }
                        result
                    }
                }.getOrNull().orEmpty()
                if (addresses.isNotEmpty()) return addresses
            }
            return null
        }
    }

    /// CookieJar с сохранением в SharedPreferences: сессия входа
    /// и cookie Anubis должны переживать перезапуск приложения.
    private class PersistentCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()
        private var prefs: android.content.SharedPreferences? = null

        @Synchronized
        fun seed(rawJson: String) {
            if (rawJson.isBlank()) return
            if (store.any { it.name == "dle_user_id" || it.name == "dle_password" }) return
            val array = runCatching { JSONArray(rawJson) }.getOrNull() ?: return
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val name = row.optString("name")
                val value = row.optString("value")
                val domain = row.optString("domain")
                if (name.isBlank() || domain.isBlank()) continue
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(row.optString("path", "/"))
                    .expiresAt(row.optLong("expires", Long.MAX_VALUE))
                if (row.optBoolean("hostOnly")) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (row.optBoolean("secure")) builder.secure()
                if (row.optBoolean("httpOnly")) builder.httpOnly()
                runCatching { builder.build() }.getOrNull()?.let { cookie ->
                    store.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                    store.add(cookie)
                }
            }
            persist()
        }

        @Synchronized
        fun clear() {
            store.clear()
            prefs?.edit()?.remove("cookies")?.apply()
        }

        @Synchronized
        fun attach(context: Context) {
            prefs = context.getSharedPreferences("hdrezka_cookies", Context.MODE_PRIVATE)
            load()
        }

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                store.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                store.add(cookie)
            }
            persist()
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            store.removeAll { it.expiresAt < now }
            return store.filter { it.matches(url) }
        }

        private fun persist() {
            val preferences = prefs ?: return
            val array = JSONArray()
            for (cookie in store) {
                array.put(
                    JSONObject()
                        .put("name", cookie.name)
                        .put("value", cookie.value)
                        .put("domain", cookie.domain)
                        .put("path", cookie.path)
                        .put("expires", cookie.expiresAt)
                        .put("secure", cookie.secure)
                        .put("httpOnly", cookie.httpOnly)
                        .put("hostOnly", cookie.hostOnly)
                )
            }
            preferences.edit().putString("cookies", array.toString()).apply()
        }

        private fun load() {
            val preferences = prefs ?: return
            val raw = preferences.getString("cookies", null) ?: return
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return
            store.clear()
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val name = row.optString("name")
                val value = row.optString("value")
                val domain = row.optString("domain")
                if (name.isBlank() || domain.isBlank()) continue
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(row.optString("path", "/"))
                    .expiresAt(row.optLong("expires", Long.MAX_VALUE))
                if (row.optBoolean("hostOnly")) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (row.optBoolean("secure")) builder.secure()
                if (row.optBoolean("httpOnly")) builder.httpOnly()
                runCatching { builder.build() }.getOrNull()?.let { store.add(it) }
            }
        }
    }
}

data class ConnectionResult(
    val url: String,
    val ok: Boolean,
    val status: String,
    val latencyMs: Long
)
