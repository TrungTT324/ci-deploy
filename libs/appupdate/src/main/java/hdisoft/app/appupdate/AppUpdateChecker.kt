package hdisoft.app.appupdate

import android.content.Context
import hdisoft.app.appupdate.data.datasource.UpdateRemoteDataSource
import hdisoft.app.appupdate.data.repository.UpdateRepositoryImpl
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.InstallState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.appupdate.domain.repository.UpdateRepository
import hdisoft.app.appupdate.domain.usecase.CheckUpdateUseCase
import hdisoft.app.appupdate.domain.usecase.DownloadApkUseCase
import hdisoft.app.core.utils.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import java.io.File

/**
 * Single entry point for integrating this module into an app: check, download,
 * verify, request the "install unknown apps" permission if needed, and install
 * (silently via root when available, otherwise via the system installer) — the
 * whole OTA lifecycle. An integrating app only needs to supply a [baseUrl]
 * (the folder the version JSON is published under) — the JSON file name
 * defaults to `<app-label-slug>-version.json`, derived from the app's own
 * `applicationInfo` label, which already matches what build.sh /
 * build_local.sh publish. Pass [jsonFileName] to override that default.
 *
 * Also automatically tracks [AppUpdateSettings.getLastCheckedAtMillis] and
 * [AppUpdateSettings.getLastApkFilePath] as a side effect of [checkUpdate]
 * and [downloadApk] — apps needing that for display don't have to track it
 * themselves.
 */
class AppUpdateChecker(
    context: Context,
    private val jsonFileName: String? = null,
    repository: UpdateRepository = UpdateRepositoryImpl(UpdateRemoteDataSource())
) {
    private val appContext = context.applicationContext
    private val checkUpdateUseCase = CheckUpdateUseCase(repository)
    private val downloadApkUseCase = DownloadApkUseCase(repository)

    fun resolveJsonUrl(baseUrl: String): String {
        val fileName = jsonFileName?.takeIf { it.isNotBlank() } ?: defaultJsonFileName(appContext)
        return baseUrl.trimEnd('/') + "/" + fileName
    }

    suspend fun checkUpdate(baseUrl: String): UpdateInfo? {
        val result = checkUpdateUseCase(resolveJsonUrl(baseUrl))
        AppUpdateSettings.setLastCheckedAtMillis(appContext, System.currentTimeMillis())
        return result
    }

    fun downloadApk(url: String, targetFile: File): Flow<DownloadState> =
        downloadApkUseCase(url, targetFile).onEach { state ->
            if (state is DownloadState.Success) {
                AppUpdateSettings.setLastApkFilePath(appContext, state.file.absolutePath)
            }
        }

    /**
     * Verifies [file] against [info], then installs it: silently via root
     * when [autoInstallIfRooted] is true (defaults to the device's actual
     * root availability), or emits [InstallState.Ready] for the caller to
     * trigger [installApk] on user confirmation otherwise.
     */
    fun installDownloadedApk(
        context: Context,
        info: UpdateInfo,
        file: File,
        autoInstallIfRooted: Boolean = DeviceUtils.isDeviceRooted()
    ): Flow<InstallState> = flow {
        emit(InstallState.Verifying(info.buildNo))
        val verification = AppUpdateInstaller.verifyApk(context.applicationContext, file, info)
        if (!verification.success) {
            file.delete()
            emit(InstallState.Error(info.buildNo, verification.message))
            return@flow
        }

        if (!autoInstallIfRooted) {
            emit(InstallState.Ready(info.buildNo, file))
            return@flow
        }

        emit(InstallState.Installing(info.buildNo))
        val result = AppUpdateInstaller.installApkRootSilent(file)
        if (result.success) {
            emit(InstallState.Success(info.buildNo))
        } else {
            emit(InstallState.Error(info.buildNo, result.output))
        }
    }.flowOn(Dispatchers.IO)

    /** Opens the system installer for a verified APK; requests the install-unknown-apps permission first if missing. */
    fun installApk(context: Context, file: File): Boolean =
        AppUpdateInstaller.installApk(context, file)

    fun cleanupTempApkFiles(cacheDir: File) = AppUpdateInstaller.cleanupTempApkFiles(cacheDir)

    companion object {
        /** `"CI-Deploy"` -> `"ci-deploy-version.json"`, `"AppMouse"` -> `"appmouse-version.json"`. */
        fun defaultJsonFileName(context: Context): String {
            val label = context.applicationInfo.loadLabel(context.packageManager).toString()
            val slug = label.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "app" }
            return "$slug-version.json"
        }
    }
}
