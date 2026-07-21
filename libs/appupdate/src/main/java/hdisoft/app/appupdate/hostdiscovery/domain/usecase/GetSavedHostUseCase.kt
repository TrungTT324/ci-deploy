package hdisoft.app.appupdate.hostdiscovery.domain.usecase

import hdisoft.app.appupdate.hostdiscovery.domain.repository.HostRepository

class GetSavedHostUseCase(private val repository: HostRepository) {
    operator fun invoke(): String? = repository.getSavedHost()
}
