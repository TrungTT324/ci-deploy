package hdisoft.app.cidata.domain.repository

import hdisoft.app.cidata.domain.model.DeviceCheckinResult

interface CidataRepository {
    suspend fun checkin(
        host: String,
        port: Int,
        deviceUid: String,
        companyCode: String,
        projectCode: String,
        model: String,
        osVersion: String,
        currentVersion: String?,
        currentBuildNo: Long?
    ): DeviceCheckinResult?
}
