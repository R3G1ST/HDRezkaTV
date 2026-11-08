package com.hdrezka.tv.data

import android.content.Context

object SearchHistoryStore {
    private const val PREFS = "hdrezka_search_history"
    private const val KEY = "queries"
    private const val LIMIT = 8

    fun list(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
    }

    fun add(context: Context, query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val items = list(context).filterNot { it.equals(clean, ignoreCase = true) }
        val updated = (listOf(clean) + items).take(LIMIT)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, updated.joinToString("\n"))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }
}
