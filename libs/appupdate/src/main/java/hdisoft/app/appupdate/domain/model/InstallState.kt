package hdisoft.app.appupdate.domain.model

import java.io.File

sealed class InstallState {
    object Idle : InstallState()
    data class Verifying(val buildNo: Long) : InstallState()
    data class Ready(val buildNo: Long, val file: File) : InstallState()
    data class Installing(val buildNo: Long) : InstallState()
    data class Success(val buildNo: Long) : InstallState()
    data class Error(val buildNo: Long, val message: String) : InstallState()
}
