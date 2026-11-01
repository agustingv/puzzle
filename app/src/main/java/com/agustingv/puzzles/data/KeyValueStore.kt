package com.agustingv.puzzles.data

import android.content.Context
import android.content.SharedPreferences

/** Small durable storage. An interface so that saving and records can be tested on the JVM. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

private class PreferencesStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = preferences.getString(key, null)

    // apply() writes to disk in the background and coalesces, so once-a-second saves stay cheap.
    override fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}

/** The app's single store: saved games and records. */
object AppStorage {
    private const val FILE = "puzzles"

    fun of(context: Context): KeyValueStore =
        PreferencesStore(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE))
}
