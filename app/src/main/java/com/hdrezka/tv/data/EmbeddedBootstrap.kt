package com.hdrezka.tv.data

import android.content.Context
import android.util.Base64
import com.hdrezka.tv.BuildConfig

/**
 * Персональная сборка: при старте подставляет аккаунт HDrezka и
 * параметры GitHub-синхронизации. В пользовательской сборке всё пусто.
 */
object EmbeddedBootstrap {
    fun init(context: Context) {
        if (!BuildConfig.EMBEDDED_PERSONAL) return

        val owner = BuildConfig.EMBEDDED_GITHUB_OWNER.trim()
        val repo = BuildConfig.EMBEDDED_GITHUB_REPO.trim()
        val token = BuildConfig.EMBEDDED_GITHUB_TOKEN.trim()
        if (owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()) {
            val existing = GithubSyncConfigStore.load(context)
            if (existing == null || existing.token.isBlank()) {
                GithubSyncConfigStore.save(context, GithubSyncConfig(owner, repo, token))
            }
        }

        val cookiesB64 = BuildConfig.EMBEDDED_COOKIES_B64.trim()
        if (cookiesB64.isNotBlank()) {
            runCatching {
                val raw = String(Base64.decode(cookiesB64, Base64.NO_WRAP))
                HdRezkaClient.seedCookies(raw)
            }
        }
    }
}
