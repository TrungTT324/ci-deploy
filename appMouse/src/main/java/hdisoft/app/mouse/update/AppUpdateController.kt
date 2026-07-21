package hdisoft.app.mouse.update

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import hdisoft.app.appupdate.AppUpdateChecker
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.InstallState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.mouse.BuildConfig
import hdisoft.app.mouse.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Self-contained OTA flow for AppMouse, built on the shared :libs:appupdate
 * module: checks a version JSON published next to the APK, and on a newer
 * build walks the user through download -> verify -> install.
 */
class AppUpdateController(private val activity: AppCompatActivity) {

    companion object {
        // Same LAN dev server build_local.sh publishes to (matches how the
        // main CI-Deploy app's WebView/update URLs are wired to this
        // machine's IP). Update this if your dev server's address changes.
        // No file name here: AppUpdateChecker derives "appmouse-version.json"
        // on its own from this app's label.
        private const val UPDATE_BASE_URL = "http://192.168.1.135:8080/ci-deploy/apps"
    }

    private val appUpdateChecker = AppUpdateChecker(activity)

    private var downloadJob: Job? = null

    fun checkForUpdate() {
        activity.lifecycleScope.launch {
            val info = try {
                appUpdateChecker.checkUpdate(UPDATE_BASE_URL)
            } catch (e: Exception) {
                null
            }
            if (info != null && info.buildNo > BuildConfig.BUILD_NO) {
                showUpdateDialog(info)
            }
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_dialog_title)
            .setMessage(
                activity.getString(
                    R.string.update_dialog_message_format,
                    info.version,
                    info.buildNo,
                    info.buildNote
                )
            )
            .setCancelable(false)
            .setNegativeButton(R.string.update_dialog_negative, null)
            .setPositiveButton(R.string.update_dialog_positive) { _, _ -> startDownload(info) }
            .show()
    }

    private fun startDownload(info: UpdateInfo) {
        val dp = activity.resources.displayMetrics.density
        val padding = (24 * dp).toInt()
        val statusView = TextView(activity).apply {
            text = activity.getString(R.string.update_download_initializing)
            textSize = 14f
        }
        val progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (16 * dp).toInt() }
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(statusView)
            addView(progressBar)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_download_title)
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(R.string.update_download_cancel, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            downloadJob?.cancel()
            dialog.dismiss()
        }

        val apkFile = File(activity.cacheDir, "AppMouse_update.apk")

        downloadJob = activity.lifecycleScope.launch {
            appUpdateChecker.downloadApk(info.url, apkFile).collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        progressBar.isIndeterminate = false
                        progressBar.progress = state.progress
                        statusView.text = activity.getString(
                            R.string.update_download_status_format,
                            state.progress,
                            state.downloadedMb,
                            state.totalMb
                        )
                    }

                    is DownloadState.Success -> {
                        dialog.dismiss()
                        verifyAndInstall(info, state.file)
                    }

                    is DownloadState.Error -> {
                        dialog.dismiss()
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.update_download_failed_format, state.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    DownloadState.Cancelled, DownloadState.Idle -> Unit
                }
            }
        }
    }

    private fun verifyAndInstall(info: UpdateInfo, file: File) {
        Toast.makeText(activity, R.string.update_verifying, Toast.LENGTH_SHORT).show()
        activity.lifecycleScope.launch {
            appUpdateChecker.installDownloadedApk(activity, info, file).collect { state ->
                when (state) {
                    is InstallState.Ready -> appUpdateChecker.installApk(activity, state.file)
                    is InstallState.Installing -> Toast.makeText(activity, R.string.update_installing, Toast.LENGTH_SHORT).show()
                    is InstallState.Success -> Toast.makeText(activity, R.string.update_install_success, Toast.LENGTH_SHORT).show()
                    is InstallState.Error -> Toast.makeText(
                        activity,
                        activity.getString(R.string.update_install_failed_format, state.message),
                        Toast.LENGTH_LONG
                    ).show()
                    InstallState.Idle, is InstallState.Verifying -> Unit
                }
            }
        }
    }
}
