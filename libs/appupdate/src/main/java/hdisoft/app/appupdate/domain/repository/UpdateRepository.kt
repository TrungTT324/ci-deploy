package hdisoft.app.appupdate.domain.repository

import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

interface UpdateRepository {
    suspend fun checkUpdate(jsonUrl: String): UpdateInfo?
    fun downloadApk(downloadUrl: String, targetFile: File): Flow<DownloadState>
}
