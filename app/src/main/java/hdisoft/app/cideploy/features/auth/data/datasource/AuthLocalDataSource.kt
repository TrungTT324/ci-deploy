package hdisoft.app.cideploy.features.auth.data.datasource

import android.content.Context
import hdisoft.app.cideploy.features.auth.domain.model.User
import org.json.JSONObject

class AuthLocalDataSource(private val context: Context) {
    private val PREFS_NAME = "CI_Deploy_Prefs"
    private val KEY_USER = "auth_user"

    fun getSavedUser(): User? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_USER, null) ?: return null
        return try {
            val json = JSONObject(jsonStr)
            User(
                id = json.getString("id"),
                username = json.getString("username"),
                token = json.getString("token"),
                deviceId = json.getString("deviceId")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveUser(user: User) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val json = JSONObject().apply {
                put("id", user.id)
                put("username", user.username)
                put("token", user.token)
                put("deviceId", user.deviceId)
            }
            prefs.edit().putString(KEY_USER, json.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearUser() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_USER).apply()
    }
}
