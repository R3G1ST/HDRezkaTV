package com.hdrezka.tv.data

import android.content.Context
import java.security.MessageDigest

object ParentalStore {
    private const val PREFS = "hdrezka_parental"
    private const val PIN_HASH = "pinHash"
    private const val ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(ENABLED, false) &&
            !prefs.getString(PIN_HASH, "").isNullOrBlank()
    }

    fun hasPin(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PIN_HASH, "").isNullOrBlank()

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PIN_HASH, hash(pin))
            .putBoolean(ENABLED, true)
            .apply()
    }

    fun enable(context: Context) {
        if (!hasPin(context)) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, true)
            .apply()
    }

    fun disable(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ENABLED, false)
            .apply()
    }

    fun verify(context: Context, pin: String): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PIN_HASH, "").orEmpty()
        return stored.isNotBlank() && stored == hash(pin)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
