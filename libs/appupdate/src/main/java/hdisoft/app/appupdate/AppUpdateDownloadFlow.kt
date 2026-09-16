package hdisoft.app.appupdate

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.InstallState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.core.utils.DeviceUtils
import java.io.File

/**
 * The full "an update is available" UI flow — upgrade prompt, download
 * progress (with Cancel), and the Ready/manual-install dialogs — as a
 * reusable component instead of each app rebuilding it. Silently downloads
 * and installs via root with no dialogs at all when [DeviceUtils.isDeviceRooted],
 * otherwise walks the user through each step; this mirrors
 * [AppUpdateChecker.installDownloadedApk]'s own `autoInstallIfRooted` default.
 *
 * Actual download/verify/install state still lives wherever the app already
 * keeps it (typically a ViewModel, so it survives configuration changes) —
 * this class only renders dialogs and asks [actions] to perform operations;
 * feed it state via [onDownloadStateChanged]/[onInstallStateChanged] from
 * that same source, e.g. from `Flow.collect`.
 *
 * One instance per screen (it holds dialog references); construct fresh if
 * the hosting Activity is recreated.
 */
class AppUpdateDownloadFlow(
    private val activity: Activity,
    private val actions: AppUpdateDownloadActions,
    private val apkFileName: String = "app_update.apk"
) {
    private var isUpgradeDialogShowing = false
    private var downloadDialog: AlertDialog? = null
    private var manualInstallDialog: AlertDialog? = null
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadPositiveButton: Button
    private lateinit var downloadNegativeButton: Button
    private var lastOfferedBuildNo: Long = 0

    private val isDownloadDialogShowing: Boolean get() = downloadDialog?.isShowing == true

    /** [downloadUrl] must already be fully resolved by the caller — host/base path resolution is app-specific. */
    fun onUpdateAvailable(info: UpdateInfo, downloadUrl: String) {
        lastOfferedBuildNo = info.buildNo
        if (DeviceUtils.isDeviceRooted()) {
            actions.clearUpdateInfo()
            startDownload(info, downloadUrl)
        } else {
            showUpgradeDialog(info, downloadUrl)
        }
    }

    private fun showUpgradeDialog(info: UpdateInfo, downloadUrl: String) {
        if (isUpgradeDialogShowing) return
        isUpgradeDialogShowing = true

        AlertDialog.Builder(activity)
            .setTitle(R.string.appupdate_flow_new_version_title)
            .setMessage(activity.getString(R.string.appupdate_flow_new_version_message_format, info.version, info.buildNote))
            .setCancelable(false)
            .setNegativeButton(R.string.appupdate_flow_decline) { dialog, _ ->
                isUpgradeDialogShowing = false
                actions.dismissVersion(info.buildNo)
                actions.clearUpdateInfo()
                actions.resumePeriodicUpdateCheck()
                dialog.dismiss()
            }
            .setPositiveButton(R.string.appupdate_flow_accept) { dialog, _ ->
                isUpgradeDialogShowing = false
                actions.clearUpdateInfo()
                dialog.dismiss()
                startDownload(info, downloadUrl)
            }
            .show()
    }

    private fun startDownload(info: UpdateInfo, downloadUrl: String) {
        if (!DeviceUtils.isDeviceRooted()) {
            buildAndShowDownloadDialog()
        }
        val apkFile = File(activity.cacheDir, apkFileName)
        actions.startDownload(info, downloadUrl, apkFile)
    }

    private fun buildAndShowDownloadDialog() {
        val dp = activity.resources.displayMetrics.density
        val padding = (24 * dp).toInt()
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        downloadStatusText = TextView(activity).apply {
            text = activity.getString(R.string.appupdate_flow_download_initializing)
            textSize = 14f
        }
        downloadProgressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, (16 * dp).toInt(), 0, 0) }
        }
        layout.addView(downloadStatusText)
        layout.addView(downloadProgressBar)

        downloadDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.appupdate_flow_download_title)
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(R.string.appupdate_flow_cancel_button, null)
            .setPositiveButton(R.string.appupdate_flow_install_button, null)
            .create()

        downloadDialog?.setOnDismissListener { actions.cancelDownload() }
        downloadDialog?.show()

        downloadPositiveButton = downloadDialog!!.getButton(AlertDialog.BUTTON_POSITIVE)
        downloadNegativeButton = downloadDialog!!.getButton(AlertDialog.BUTTON_NEGATIVE)
        downloadPositiveButton.visibility = View.GONE
        downloadNegativeButton.setOnClickListener {
            actions.cancelDownload()
            downloadDialog?.dismiss()
        }
    }

    /** Feed every emission from the app's `Flow<DownloadState>` here. */
    fun onDownloadStateChanged(state: DownloadState) {
        if (DeviceUtils.isDeviceRooted()) {
            // Silent path: no dialog was ever shown; just drive the next action.
            when (state) {
                is DownloadState.Success -> actions.verifyDownloadedApk(state.file, autoInstallWithRoot = true)
                is DownloadState.Error -> actions.resetUpdateOperation()
                is DownloadState.Cancelled -> actions.resetUpdateOperation()
                else -> Unit
            }
            return
        }

        if (!isDownloadDialogShowing) return

        when (state) {
            is DownloadState.Idle -> {
                downloadStatusText.text = activity.getString(R.string.appupdate_flow_download_initializing)
                downloadProgressBar.isIndeterminate = true
            }
            is DownloadState.Downloading -> {
                if (state.totalMb > 0) {
                    downloadProgressBar.isIndeterminate = false
                    downloadProgressBar.progress = state.progress
                    downloadStatusText.text = activity.getString(
                        R.string.appupdate_flow_download_progress_format,
                        state.downloadedMb, state.totalMb, state.progress
                    )
                } else {
                    downloadProgressBar.isIndeterminate = true
                    downloadStatusText.text = activity.getString(
                        R.string.appupdate_flow_download_progress_indeterminate_format,
                        state.downloadedMb
                    )
                }
            }
            is DownloadState.Success -> {
                downloadStatusText.text = activity.getString(R.string.appupdate_flow_verifying)
                downloadProgressBar.isIndeterminate = true
                actions.verifyDownloadedApk(state.file, autoInstallWithRoot = false)
            }
            is DownloadState.Cancelled -> {
                downloadStatusText.text = activity.getString(R.string.appupdate_flow_download_cancelled)
                setDownloadDialogCloseAction()
            }
            is DownloadState.Error -> {
                downloadStatusText.text = activity.getString(R.string.appupdate_flow_download_error_format, state.message)
                setDownloadDialogCloseAction()
            }
        }
    }

    private fun setDownloadDialogCloseAction() {
        downloadNegativeButton.text = activity.getString(R.string.appupdate_close_button)
        downloadNegativeButton.setOnClickListener {
            actions.resetUpdateOperation()
            downloadDialog?.dismiss()
        }
    }

    /** Feed every emission from the app's `Flow<InstallState>` here. */
    fun onInstallStateChanged(state: InstallState) {
        when (state) {
            is InstallState.Ready -> {
                if (isDownloadDialogShowing) showManualInstallUi(state.file) else showReadyInstallDialog(state.file)
            }
            is InstallState.Error -> {
                if (DeviceUtils.isDeviceRooted()) {
                    val file = File(activity.cacheDir, apkFileName)
                    if (file.exists()) {
                        showManualInstallFallbackDialog(file, state.message)
                    } else {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.appupdate_flow_update_failed_toast_format, state.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else if (isDownloadDialogShowing) {
                    downloadStatusText.text = activity.getString(R.string.appupdate_flow_verification_failed_format, state.message)
                    downloadProgressBar.isIndeterminate = false
                    setDownloadDialogCloseAction()
                }
            }
            else -> Unit
        }
    }

    private fun showManualInstallUi(file: File) {
        downloadStatusText.text = activity.getString(R.string.appupdate_flow_download_completed)
        downloadProgressBar.progress = 100
        downloadProgressBar.isIndeterminate = false

        downloadPositiveButton.visibility = View.VISIBLE
        downloadPositiveButton.setOnClickListener { actions.installApk(file) }

        downloadNegativeButton.visibility = View.VISIBLE
        downloadNegativeButton.text = activity.getString(R.string.appupdate_flow_later_button)
        downloadNegativeButton.setOnClickListener {
            actions.resetUpdateOperation()
            downloadDialog?.dismiss()
        }
    }

    private fun showReadyInstallDialog(file: File) {
        if (!file.exists() || activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.appupdate_flow_ready_title)
            .setMessage(R.string.appupdate_flow_ready_message)
            .setCancelable(false)
            .setPositiveButton(R.string.appupdate_flow_install_button) { dialog, _ ->
                actions.installApk(file)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.appupdate_flow_later_button) { dialog, _ ->
                actions.resetUpdateOperation()
                dialog.dismiss()
            }
            .show()
    }

    private fun showManualInstallFallbackDialog(file: File, reason: String) {
        if (manualInstallDialog?.isShowing == true) return
        manualInstallDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.appupdate_flow_manual_install_title)
            .setMessage(activity.getString(R.string.appupdate_flow_manual_install_message_format, reason))
            .setCancelable(false)
            .setPositiveButton(R.string.appupdate_flow_install_button) { _, _ ->
                actions.installApk(file)
            }
            .setNegativeButton(R.string.appupdate_flow_skip_button) { _, _ ->
                actions.dismissVersion(lastOfferedBuildNo)
                actions.resetUpdateOperation()
            }
            .create()
        manualInstallDialog?.setOnDismissListener { manualInstallDialog = null }
        manualInstallDialog?.show()
    }
}
