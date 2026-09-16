package hdisoft.app.appupdate.domain.usecase

import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.repository.UpdateRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

class DownloadApkUseCase(private val repository: UpdateRepository) {
    operator fun invoke(url: String, targetFile: File): Flow<DownloadState> {
        return repository.downloadApk(url, targetFile)
    }
}
