package hdisoft.app.appupdate.domain.usecase

import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.appupdate.domain.repository.UpdateRepository

class CheckUpdateUseCase(private val repository: UpdateRepository) {
    suspend operator fun invoke(jsonUrl: String): UpdateInfo? {
        return repository.checkUpdate(jsonUrl)
    }
}
