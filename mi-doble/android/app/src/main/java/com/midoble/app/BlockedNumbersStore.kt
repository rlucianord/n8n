package com.midoble.app

import android.content.Context

object BlockedNumbersStore {
    private const val PREFS = "mi_doble_profile"
    private const val KEY = "blocked_phone_numbers"
    fun read(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().sorted()
    fun write(context: Context, numbers: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, numbers.toSet()).apply()
    }
}
