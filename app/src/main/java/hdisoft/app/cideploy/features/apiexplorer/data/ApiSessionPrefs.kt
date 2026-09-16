package hdisoft.app.cideploy.features.apiexplorer.data

import android.content.Context

class ApiSessionPrefs(private val context: Context) {
    private val PREFS_NAME = "CI_ApiExplorer_Prefs"
    private val KEY_HOST = "host"
    private val KEY_PORT = "port"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHost(default: String = ""): String = prefs().getString(KEY_HOST, default) ?: default
    fun getPort(default: Int = 8080): Int = prefs().getInt(KEY_PORT, default)
    fun getUsername(default: String = "admin"): String = prefs().getString(KEY_USERNAME, default) ?: default
    fun getPassword(default: String = "admin"): String = prefs().getString(KEY_PASSWORD, default) ?: default

    fun save(host: String, port: Int, username: String, password: String) {
        prefs().edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }
}
