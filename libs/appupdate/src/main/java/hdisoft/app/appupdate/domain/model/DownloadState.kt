package hdisoft.app.appupdate.domain.model

import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedMb: Double, val totalMb: Double) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
    object Cancelled : DownloadState()
}
