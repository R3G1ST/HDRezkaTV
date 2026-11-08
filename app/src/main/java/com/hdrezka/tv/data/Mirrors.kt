package com.hdrezka.tv.data

import android.content.Context

/**
 * Зеркала сайта: встроенные, пользовательские и активное.
 * Выбор зеркала переносится на все сохранённые ссылки, чтобы история
 * и избранное продолжали открываться после переключения.
 */
object Mirrors {
    private const val PREFS = "hdrezka_mirrors"
    private const val CUSTOM = "custom"
    private const val ACTIVE = "active"

    val builtin = listOf(
        "https://hdrezka.ag",
        "https://hdrezka-home.tv",
        "https://hdrezka.tv",
        "https://hdrezka.me",
        "https://rezka.ag"
    )

    fun normalize(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return ""
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://$value"
        }
        return value.trimEnd('/')
    }

    fun host(raw: String): String {
        val normalized = normalize(raw)
        return runCatching { java.net.URI(normalized).host }.getOrNull()
            ?: normalized.substringAfter("://")
    }

    fun isValid(raw: String): Boolean {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return false
        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return false
        val host = uri.host ?: return false
        return host.contains(".") && (uri.scheme == "https" || uri.scheme == "http")
    }

    fun custom(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CUSTOM, null)
            ?.split('\n')
            ?.map { normalize(it) }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?: emptyList()

    fun all(context: Context): List<String> = (custom(context) + builtin).distinct()

    fun active(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACTIVE, null)
            ?.takeIf { it.isNotBlank() }

    fun addCustom(context: Context, raw: String): Boolean {
        val normalized = normalize(raw)
        if (!isValid(normalized)) return false
        if (all(context).contains(normalized)) return false
        val list = custom(context) + normalized
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CUSTOM, list.joinToString("\n"))
            .apply()
        return true
    }

    fun removeCustom(context: Context, raw: String) {
        val normalized = normalize(raw)
        val list = custom(context).filter { it != normalized }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CUSTOM, list.joinToString("\n"))
            .apply()
        if (active(context) == normalized) {
            apply(context, builtin.first())
        }
    }

    fun apply(context: Context, raw: String) {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(ACTIVE, normalized)
            .apply()
        HdRezkaClient.setBaseUrl(normalized)
        ProgressStore.rewriteHost(context, normalized)
        LibraryStore.rewriteHost(context, normalized)
        ReleaseWatchStore.rewriteHost(context, normalized)
    }
}

object SiteUrl {
    /** Заменяет схему и хост у абсолютной ссылки, сохраняя путь и query. */
    /** Локализует ссылку тайтла и переводит пути inc на домашнее зеркало. */
    fun localizeItem(raw: String, base: String): String {
        val localized = localize(raw, base)
        return localized
            .replaceFirst("/serialy/", "/series/")
            .replaceFirst("/filmy/", "/films/")
    }

    fun localize(raw: String, base: String): String {
        if (raw.isBlank()) return raw
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return raw
        val path = uri.rawPath
        if (path.isNullOrBlank()) return raw
        val cleanBase = base.trimEnd('/')
        return buildString {
            append(cleanBase)
            append(path)
            if (!uri.rawQuery.isNullOrBlank()) append('?').append(uri.rawQuery)
        }
    }
}
