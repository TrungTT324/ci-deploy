package hdisoft.app.appupdate.domain.model

data class UpdateInfo(
    val buildNo: Long,
    val version: String,
    val buildNote: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val packageName: String
)
