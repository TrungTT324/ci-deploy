package hdisoft.app.cidata.domain.model

data class DeviceCheckinResult(
    val latestRelease: LatestRelease?
)

data class LatestRelease(
    val version: String,
    val buildNo: Long?,
    val releaseNote: String,
    val sourceUrl: String
)
