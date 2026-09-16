package hdisoft.app.cidata.data.repository

import hdisoft.app.cidata.data.datasource.CidataRemoteDataSource
import hdisoft.app.cidata.domain.model.DeviceCheckinResult
import hdisoft.app.cidata.domain.repository.CidataRepository

class CidataRepositoryImpl(
    private val remoteDataSource: CidataRemoteDataSource
) : CidataRepository {

    override suspend fun checkin(
        host: String,
        port: Int,
        deviceUid: String,
        companyCode: String,
        projectCode: String,
        model: String,
        osVersion: String,
        currentVersion: String?,
        currentBuildNo: Long?
    ): DeviceCheckinResult? {
        return remoteDataSource.checkin(
            host = host,
            port = port,
            deviceUid = deviceUid,
            companyCode = companyCode,
            projectCode = projectCode,
            model = model,
            osVersion = osVersion,
            currentVersion = currentVersion,
            currentBuildNo = currentBuildNo
        )
    }
}
