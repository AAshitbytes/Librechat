package com.example.librechat

import android.content.Context

/**
 * The two things the app keeps between runs, stored with SharedPreferences, which is the small
 * key and value store Android gives every app.
 *
 * Chat messages are deliberately not kept, only who this phone is.
 */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("librechat", Context.MODE_PRIVATE)

    /** The name the user typed on the first run. Empty means they have not chosen one yet. */
    var name: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_NAME, value).apply()
        }

    /**
     * The id of this phone. It is made up once on the first run and then kept, so other phones
     * still recognise this one after the app is closed and opened again.
     */
    val id: String
        get() {
            val saved = prefs.getString(KEY_ID, null)
            if (saved != null) return saved

            val fresh = Packet.randomHex(4)
            prefs.edit().putString(KEY_ID, fresh).apply()
            return fresh
        }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_ID = "id"
    }
}
