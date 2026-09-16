package hdisoft.app.appupdate.data.repository

import hdisoft.app.appupdate.data.datasource.UpdateRemoteDataSource
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.appupdate.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

class UpdateRepositoryImpl(
    private val remoteDataSource: UpdateRemoteDataSource
) : UpdateRepository {

    override suspend fun checkUpdate(jsonUrl: String): UpdateInfo? {
        return remoteDataSource.checkUpdate(jsonUrl)
    }

    override fun downloadApk(downloadUrl: String, targetFile: File): Flow<DownloadState> {
        return remoteDataSource.downloadApk(downloadUrl, targetFile)
    }
}
