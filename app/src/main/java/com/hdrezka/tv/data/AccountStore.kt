package com.hdrezka.tv.data

import android.content.Context

object AccountStore {
    private const val PREFS = "hdrezka_account"

    fun load(context: Context): AccountInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString("id", null) ?: return null
        return AccountInfo(
            id = id,
            name = prefs.getString("name", "Пользователь") ?: "Пользователь",
            isPremium = prefs.getBoolean("premium", false)
        )
    }

    fun save(context: Context, info: AccountInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("id", info.id)
            .putString("name", info.name)
            .putBoolean("premium", info.isPremium)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
