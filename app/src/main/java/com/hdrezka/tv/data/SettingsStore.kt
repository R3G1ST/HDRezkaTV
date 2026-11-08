package com.hdrezka.tv.data

import android.content.Context

object SettingsStore {
    private const val PREFS = "hdrezka_settings"

    fun defaultQuality(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("quality", "1080p") ?: "1080p"

    fun setDefaultQuality(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("quality", value)
            .apply()
    }

    fun autoplayNext(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("autoplayNext", true)

    fun setAutoplayNext(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("autoplayNext", value)
            .apply()
    }

    fun useVimu(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("useVimu", false)

    fun setUseVimu(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("useVimu", value)
            .apply()
    }

    /**
     * Один раз переводим существующую установку на встроенный плеер.
     * Дальше пользователь может снова включить Vimu в настройках.
     */
    fun applyBuiltInPlayerDefault(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("builtInPlayerDefaultV1", false)) return
        prefs.edit()
            .putBoolean("useVimu", false)
            .putBoolean("builtInPlayerDefaultV1", true)
            .apply()
    }

    fun subtitlesDefault(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("subtitlesDefault", true)

    fun setSubtitlesDefault(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("subtitlesDefault", value)
            .apply()
    }
}
