package hdisoft.app.appupdate

import android.content.Context
import hdisoft.app.core.prefs.PrefsStore

/**
 * Reusable, persisted settings for [AppUpdateChecker]: how often to
 * re-check for an update, and which host to check against. Stored in the
 * integrating app's own SharedPreferences (isolated per package
 * automatically, no app-specific wiring needed), so any app using this
 * module gets the same settings/options instead of reimplementing its own.
 */
object AppUpdateSettings {

    private const val PREFS_NAME = "appupdate_settings"
    private const val KEY_CHECK_INTERVAL_SECONDS = "check_interval_seconds"
    private const val KEY_UPDATE_SOURCE_MODE = "update_source_mode"
    private const val KEY_LAST_CHECK_AT_MILLIS = "last_check_at_millis"
    private const val KEY_LAST_APK_FILE_PATH = "last_apk_file_path"

    /** 0 means "no periodic timer — only check on app open/resume". */
    const val CHECK_INTERVAL_DEFAULT = 0

    /** Canonical set of interval choices apps should offer in their settings UI. */
    val CHECK_INTERVAL_OPTIONS: List<Int> = listOf(0, 10, 30, 120)

    /**
     * Which host an app should build its `checkUpdate(baseUrl)` call against.
     * - [LAN_ONLY]: use a host found via LAN discovery (each app's own logic).
     * - [PUBLIC_HOST]: skip LAN discovery, use [DEFAULT_PUBLIC_BASE_URL].
     */
    enum class UpdateSourceMode { LAN_ONLY, PUBLIC_HOST }

    private val DEFAULT_SOURCE_MODE = UpdateSourceMode.LAN_ONLY

    // TODO: replace with the real publicly reachable base URL (folder holding
    // <app-name-slug>-version.json, no file name) once one exists; PUBLIC_HOST
    // mode is unusable until then.
    const val DEFAULT_PUBLIC_BASE_URL = "https://TODO-public-host.example.com/ci-deploy"

    private fun prefs(context: Context) = PrefsStore(context, PREFS_NAME)

    fun getCheckIntervalSeconds(context: Context): Int =
        prefs(context).getInt(KEY_CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_DEFAULT)

    fun setCheckIntervalSeconds(context: Context, seconds: Int) {
        prefs(context).putInt(KEY_CHECK_INTERVAL_SECONDS, seconds)
    }

    fun getUpdateSourceMode(context: Context): UpdateSourceMode {
        val name = prefs(context).getString(KEY_UPDATE_SOURCE_MODE) ?: return DEFAULT_SOURCE_MODE
        return try {
            UpdateSourceMode.valueOf(name)
        } catch (e: IllegalArgumentException) {
            DEFAULT_SOURCE_MODE
        }
    }

    fun setUpdateSourceMode(context: Context, mode: UpdateSourceMode) {
        prefs(context).putString(KEY_UPDATE_SOURCE_MODE, mode.name)
    }

    /** Epoch millis of the last `checkUpdate()` call, or 0 if never checked. Set automatically by [AppUpdateChecker]. */
    fun getLastCheckedAtMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK_AT_MILLIS, 0L)

    internal fun setLastCheckedAtMillis(context: Context, millis: Long) {
        prefs(context).putLong(KEY_LAST_CHECK_AT_MILLIS, millis)
    }

    /** Absolute path of the most recently *downloaded* APK, or null if none yet. Set automatically by [AppUpdateChecker]. */
    fun getLastApkFilePath(context: Context): String? =
        prefs(context).getString(KEY_LAST_APK_FILE_PATH)

    internal fun setLastApkFilePath(context: Context, path: String) {
        prefs(context).putString(KEY_LAST_APK_FILE_PATH, path)
    }
}
