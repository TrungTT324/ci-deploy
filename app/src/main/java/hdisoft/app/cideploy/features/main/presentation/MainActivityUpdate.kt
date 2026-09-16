package hdisoft.app.cideploy.features.main.presentation

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import hdisoft.app.appupdate.AppUpdateInfoDialog
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.InstallState
import hdisoft.app.cideploy.BuildConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Extension functions for [MainActivity] that group together all logic
 * related to handling App Update flows, observers, and settings dialogs.
 */
internal fun MainActivity.setupAppUpdateObservers() {
    viewModel.updateInfo.observe(this) { info ->
        if (info != null && info.buildNo > BuildConfig.BUILD_NO) {
            val downloadState = viewModel.downloadState.value
            if (downloadState is DownloadState.Downloading || downloadState is DownloadState.Success) {
                android.util.Log.d("CI_DEPLOY_ROOT", "Observer triggered but download/install is active. Ignoring.")
                return@observe
            }
            viewModel.stopPeriodicUpdateCheck()
            val host = viewModel.currentHost.value
            if (host != null) {
                android.util.Log.d("CI_DEPLOY_ROOT", "New update version found: ${info.version} (Build: ${info.buildNo})")
                var finalDownloadUrl = info.url
                try {
                    val uri = Uri.parse(info.url)
                    val fileName = uri.lastPathSegment ?: "CI-Deploy_debug.apk"
                    finalDownloadUrl = "http://$host:8080/$urlPath/$fileName"
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                updateDownloadFlow.onUpdateAvailable(info, finalDownloadUrl)
            }
        }
    }

    lifecycleScope.launch {
        viewModel.downloadState.collectLatest { state ->
            updateDownloadFlow.onDownloadStateChanged(state)
        }
    }

    lifecycleScope.launch {
        viewModel.installState.collectLatest { state ->
            updateDownloadFlow.onInstallStateChanged(state)
        }
    }
}

internal fun MainActivity.showAppUpdateInfoDialog() {
    AppUpdateInfoDialog.show(
        context = this,
        versionName = BuildConfig.VERSION_NAME,
        buildNo = BuildConfig.BUILD_NO,
        onIntervalSaved = { interval -> viewModel.setCheckInterval(interval) },
        onSourceModeSaved = {
            viewModel.currentHost.value?.let { host -> viewModel.checkForUpdates(host) }
            // Safe to call unconditionally: startDiscovery() self-gates on the
            // source mode and no-ops (Disabled) when Public host is selected.
            discoveryService?.startDiscovery()
        }
    )
}
