package com.hdrezka.tv.data

import android.content.Context
import com.hdrezka.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GithubOAuth {
    private val clientId: String get() = BuildConfig.GITHUB_OAUTH_CLIENT_ID
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val formType = "application/x-www-form-urlencoded".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    )

    data class TokenResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAt: Long
    )

    suspend fun requestDeviceCode(): DeviceCode = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(
                "client_id=$clientId&scope=repo".toRequestBody(formType)
            )
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
            val json = JSONObject(body)
            val device = json.optString("device_code")
            val code = json.optString("user_code")
            if (device.isBlank() || code.isBlank()) {
                error(json.optString("error_description").ifBlank { "Не удалось получить код" })
            }
            DeviceCode(
                deviceCode = device,
                userCode = code,
                verificationUri = json.optString(
                    "verification_uri",
                    "https://github.com/login/device"
                ),
                expiresIn = json.optInt("expires_in", 900),
                interval = json.optInt("interval", 5)
            )
        }
    }

    suspend fun pollForToken(
        deviceCode: String,
        intervalSeconds: Int
    ): TokenResult = withContext(Dispatchers.IO) {
        var interval = intervalSeconds.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + 15L * 60_000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val body = "client_id=$clientId" +
                "&device_code=${deviceCode}" +
                "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(body.toRequestBody(formType))
                .build()
            val token = client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                JSONObject(raw)
            }
            val access = token.optString("access_token")
            if (access.isNotBlank()) {
                val expiresIn = token.optLong("expires_in", 0L)
                return@withContext TokenResult(
                    accessToken = access,
                    refreshToken = token.optString("refresh_token")
                        .takeIf { it.isNotBlank() },
                    expiresAt = if (expiresIn > 0) {
                        System.currentTimeMillis() + expiresIn * 1000L - 60_000L
                    } else {
                        0L
                    }
                )
            }
            when (token.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("Код истёк. Начните вход заново.")
                "access_denied" -> error("Вход отменён.")
                else -> error(
                    token.optString("error_description")
                        .ifBlank { "Ожидание подтверждения…" }
                )
            }
        }
        error("Время ожидания входа истекло")
    }

    /** Создаёт приватный репозиторий hdrezka-sync и возвращает логин. */
    suspend fun createPrivateRepo(token: String): String = withContext(Dispatchers.IO) {
        val loginRequest = Request.Builder()
            .url("https://api.github.com/user")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HDrezkaTV")
            .get()
            .build()
        val login = client.newCall(loginRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
            JSONObject(raw).optString("login")
        }
        if (login.isBlank()) error("Не удалось определить GitHub-аккаунт")

        val payload = JSONObject()
            .put("name", "hdrezka-sync")
            .put("private", true)
            .put("description", "HDrezka watch progress")
            .put("auto_init", true)
            .toString()
        val createRequest = Request.Builder()
            .url("https://api.github.com/user/repos")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "HDrezkaTV")
            .post(payload.toRequestBody(jsonType))
            .build()
        client.newCall(createRequest).execute().use { response ->
            if (response.code == 422) {
                // Репозиторий уже существует — просто убеждаемся, что он приватный.
                val patch = JSONObject().put("private", true).toString()
                val privateRequest = Request.Builder()
                    .url("https://api.github.com/repos/$login/hdrezka-sync")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "HDrezkaTV")
                    .patch(patch.toRequestBody(jsonType))
                    .build()
                runCatching { client.newCall(privateRequest).execute().close() }
            } else if (!response.isSuccessful) {
                error("GitHub: HTTP ${response.code}")
            }
        }
        login
    }

    suspend fun refresh(config: GithubSyncConfig): GithubSyncConfig =
        withContext(Dispatchers.IO) {
            val refreshToken = config.refreshToken
                ?: error("Нет refresh_token")
            val body = "client_id=$clientId" +
                "&grant_type=refresh_token" +
                "&refresh_token=$refreshToken"
            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(body.toRequestBody(formType))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("GitHub: HTTP ${response.code}")
                val json = JSONObject(raw)
                val access = json.optString("access_token")
                if (access.isBlank()) {
                    error(json.optString("error_description").ifBlank {
                        "Не удалось обновить токен"
                    })
                }
                val expiresIn = json.optLong("expires_in", 0L)
                config.copy(
                    token = access,
                    refreshToken = json.optString("refresh_token")
                        .takeIf { it.isNotBlank() } ?: refreshToken,
                    expiresAt = if (expiresIn > 0) {
                        System.currentTimeMillis() + expiresIn * 1000L - 60_000L
                    } else {
                        0L
                    }
                )
            }
        }

    /** Конфиг с автоматическим обновлением access token при необходимости. */
    suspend fun authorizedConfig(context: Context): GithubSyncConfig? {
        val config = GithubSyncConfigStore.load(context) ?: return null
        val needsRefresh = config.refreshToken != null &&
            config.expiresAt > 0 &&
            config.expiresAt - System.currentTimeMillis() < 5L * 60_000L
        if (!needsRefresh) return config
        val refreshed = runCatching { refresh(config) }.getOrNull()
        if (refreshed != null) {
            GithubSyncConfigStore.save(context, refreshed)
            return refreshed
        }
        return config
    }
}
