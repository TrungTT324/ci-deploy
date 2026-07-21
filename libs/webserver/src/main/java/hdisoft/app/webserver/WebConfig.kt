package hdisoft.app.webserver

import android.content.Context

class WebConfig(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "CI_Deploy_Prefs"
        const val KEY_REQUIRE_AUTH = "web_require_authen"
        const val KEY_AUTH_PIN = "web_auth_pin"
        const val KEY_PORT = "web_port"
    }

    /** Persisted port override, if the user ever changes it at runtime; [default] is used until then. */
    fun getPort(default: Int): Int = prefs.getInt(KEY_PORT, default)

    fun setPort(value: Int) {
        prefs.edit().putInt(KEY_PORT, value).apply()
    }

    var requireAuthen: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_AUTH, false) // default false
        set(value) {
            prefs.edit().putBoolean(KEY_REQUIRE_AUTH, value).apply()
        }

    var authPin: String
        get() {
            var pin = prefs.getString(KEY_AUTH_PIN, null)
            if (pin == null || pin.length != 4) {
                pin = generateNewPin()
            }
            return pin
        }
        set(value) {
            prefs.edit().putString(KEY_AUTH_PIN, value).apply()
        }

    fun generateNewPin(): String {
        val newPin = (1000..9999).random().toString() // generate 4 digit PIN
        prefs.edit().putString(KEY_AUTH_PIN, newPin).apply()
        return newPin
    }
}
