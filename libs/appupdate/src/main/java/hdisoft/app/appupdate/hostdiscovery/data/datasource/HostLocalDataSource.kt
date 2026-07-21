package hdisoft.app.appupdate.hostdiscovery.data.datasource

import android.content.Context
import hdisoft.app.core.prefs.PrefsStore

class HostLocalDataSource(context: Context) {
    private val prefs = PrefsStore(context, PREFS_NAME)

    fun getSavedHost(): String? = prefs.getString(KEY_HOST)

    fun saveHost(host: String) {
        prefs.putString(KEY_HOST, host)
    }

    companion object {
        private const val PREFS_NAME = "CI_Deploy_Prefs"
        private const val KEY_HOST = "ci_deploy_host"
    }
}
