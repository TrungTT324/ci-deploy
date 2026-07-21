package hdisoft.app.appupdate

import hdisoft.app.appupdate.domain.model.UpdateInfo
import java.io.File

/**
 * The ViewModel-side operations [AppUpdateDownloadFlow] needs to drive an
 * update through download → verify → install. An app implements this as a
 * thin adapter over its own ViewModel (state ownership stays there, so it
 * survives configuration changes — this class only renders dialogs and never
 * holds the actual download/install state itself).
 */
interface AppUpdateDownloadActions {
    fun startDownload(info: UpdateInfo, url: String, targetFile: File)
    fun cancelDownload()
    fun verifyDownloadedApk(file: File, autoInstallWithRoot: Boolean)
    fun installApk(file: File)
    fun resetUpdateOperation()
    fun dismissVersion(buildNo: Long)
    fun clearUpdateInfo()
    fun resumePeriodicUpdateCheck()
}
