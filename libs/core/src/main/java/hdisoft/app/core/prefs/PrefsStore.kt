package hdisoft.app.core.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin, typed wrapper over Android SharedPreferences. Several modules in this
 * repo previously hand-rolled the same `context.getSharedPreferences(name,
 * MODE_PRIVATE)` + `.edit().put...().apply()` boilerplate; this centralizes
 * it so a new prefs-backed class only has to declare its file name and keys.
 */
class PrefsStore(context: Context, name: String) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun getString(key: String, default: String? = null): String? = prefs.getString(key, default)
    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getInt(key: String, default: Int = 0): Int = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = prefs.getLong(key, default)
    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    /** Batches multiple writes into a single commit, e.g. saving several related fields together. */
    fun edit(action: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.action()
        editor.apply()
    }
}
