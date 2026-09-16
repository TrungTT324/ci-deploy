package hdisoft.app.appupdate

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import hdisoft.app.appupdate.AppUpdateSettings.UpdateSourceMode
import hdisoft.app.core.prefs.PrefsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared "version info" dialog: shows the installed version/build/install
 * date plus [AppUpdateSettings]'s check-interval/update-source settings and
 * last-check/last-apk history — a ready-made settings UI over this module,
 * for any app to reuse instead of building its own.
 *
 * All display text comes from this module's own string resources
 * (`values/strings.xml` default = English, `values-vi/strings.xml` =
 * Vietnamese) resolved against the [Context] passed in, so it automatically
 * follows whatever locale the integrating app itself is running in — no
 * locale-picking logic needed here.
 *
 * [versionName]/[buildNo] are taken as parameters (not read from a
 * `BuildConfig`) since each integrating app has its own; everything else is
 * sourced entirely from this module.
 */
object AppUpdateInfoDialog {

    private const val PREFS_NAME = "appupdate_info_dialog"

    private fun intervalLabel(context: Context, seconds: Int): String =
        if (seconds <= 0) context.getString(R.string.appupdate_interval_default)
        else context.getString(R.string.appupdate_interval_every_seconds, seconds)

    private fun sourceModeLabel(context: Context, mode: UpdateSourceMode): String = when (mode) {
        UpdateSourceMode.LAN_ONLY -> context.getString(R.string.appupdate_source_mode_lan)
        UpdateSourceMode.PUBLIC_HOST -> context.getString(R.string.appupdate_source_mode_public)
    }

    private fun formatTimestamp(context: Context, millis: Long): String {
        if (millis <= 0L) return context.getString(R.string.appupdate_last_checked_never)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun getInstallDate(context: Context, buildNo: Long): String {
        val prefs = PrefsStore(context, PREFS_NAME)
        val buildKey = "install_date_$buildNo"
        prefs.getString(buildKey)?.let { return it }

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val installDate: String = sdf.format(Date())
        prefs.putString(buildKey, installDate)
        return installDate
    }

    /**
     * [onIntervalSaved] is invoked with the newly picked interval, and
     * [onSourceModeSaved] with the newly picked update source, so the caller
     * can push either into its ViewModel (e.g. to trigger an immediate re-check).
     */
    fun show(
        context: Context,
        versionName: String,
        buildNo: Long,
        onIntervalSaved: (Int) -> Unit = {},
        onSourceModeSaved: (UpdateSourceMode) -> Unit = {}
    ) {
        val installDate = getInstallDate(context, buildNo)
        val savedInterval = AppUpdateSettings.getCheckIntervalSeconds(context)
        val savedSourceMode = AppUpdateSettings.getUpdateSourceMode(context)
        val lastCheckedAt = AppUpdateSettings.getLastCheckedAtMillis(context)
        val lastApkFileName = AppUpdateSettings.getLastApkFilePath(context)?.let { File(it).name }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val tvVersionInfo = TextView(context).apply {
            text = context.getString(R.string.appupdate_version_info_format, versionName, buildNo)
            textSize = 16f
        }
        layout.addView(tvVersionInfo)

        val tvInstallDate = TextView(context).apply {
            text = context.getString(R.string.appupdate_install_date_label, installDate)
            textSize = 14f
            val topMargin = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }
        layout.addView(tvInstallDate)

        val tvLastChecked = TextView(context).apply {
            text = context.getString(R.string.appupdate_last_checked_label, formatTimestamp(context, lastCheckedAt))
            textSize = 14f
        }
        layout.addView(tvLastChecked)

        val tvLastApk = TextView(context).apply {
            text = context.getString(
                R.string.appupdate_last_apk_label,
                lastApkFileName ?: context.getString(R.string.appupdate_none_placeholder)
            )
            textSize = 14f
        }
        layout.addView(tvLastApk)

        val tvIntervalHeader = TextView(context).apply {
            text = context.getString(R.string.appupdate_interval_header)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            val topMargin = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }
        layout.addView(tvIntervalHeader)

        val radioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        AppUpdateSettings.CHECK_INTERVAL_OPTIONS.forEach { seconds ->
            val radioButton = RadioButton(context).apply {
                text = intervalLabel(context, seconds)
                id = seconds
                isChecked = (seconds == savedInterval)
            }
            radioGroup.addView(radioButton)
        }
        layout.addView(radioGroup)

        val tvSourceHeader = TextView(context).apply {
            text = context.getString(R.string.appupdate_source_header)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            val topMargin = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, 0)
        }
        layout.addView(tvSourceHeader)

        val sourceRadioGroup = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
        }
        val sourceModeByViewId = mutableMapOf<Int, UpdateSourceMode>()
        UpdateSourceMode.entries.forEach { mode ->
            val radioButton = RadioButton(context).apply {
                id = View.generateViewId()
                text = sourceModeLabel(context, mode)
                isChecked = (mode == savedSourceMode)
            }
            sourceModeByViewId[radioButton.id] = mode
            sourceRadioGroup.addView(radioButton)
        }
        layout.addView(sourceRadioGroup)

        AlertDialog.Builder(context)
            .setTitle(R.string.appupdate_dialog_title)
            .setView(layout)
            .setPositiveButton(R.string.appupdate_save_button) { _, _ ->
                val selectedInterval = radioGroup.checkedRadioButtonId
                AppUpdateSettings.setCheckIntervalSeconds(context, selectedInterval)
                onIntervalSaved(selectedInterval)

                val selectedSourceMode = sourceModeByViewId[sourceRadioGroup.checkedRadioButtonId] ?: savedSourceMode
                AppUpdateSettings.setUpdateSourceMode(context, selectedSourceMode)
                onSourceModeSaved(selectedSourceMode)

                Toast.makeText(context, R.string.appupdate_saved_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.appupdate_close_button, null)
            .show()
    }
}
