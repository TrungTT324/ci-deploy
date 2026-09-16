package hdisoft.app.cidata.domain.usecase

import android.os.Build
import hdisoft.app.cidata.CidataConstants
import hdisoft.app.cidata.domain.model.DeviceCheckinResult
import hdisoft.app.cidata.domain.repository.CidataRepository

class DeviceCheckinUseCase(private val repository: CidataRepository) {
    suspend operator fun invoke(
        host: String,
        deviceUid: String,
        currentVersion: String? = null,
        currentBuildNo: Long? = null,
        companyCode: String = CidataConstants.DEFAULT_COMPANY_CODE,
        projectCode: String = CidataConstants.DEFAULT_PROJECT_CODE,
        port: Int = CidataConstants.DEFAULT_PORT,
        model: String = Build.MODEL,
        osVersion: String = Build.VERSION.RELEASE
    ): DeviceCheckinResult? {
        return repository.checkin(
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
