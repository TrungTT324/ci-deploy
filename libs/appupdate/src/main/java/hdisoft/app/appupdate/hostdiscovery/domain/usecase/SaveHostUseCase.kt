package hdisoft.app.appupdate.hostdiscovery.domain.usecase

import hdisoft.app.appupdate.hostdiscovery.domain.repository.HostRepository

class SaveHostUseCase(private val repository: HostRepository) {
    operator fun invoke(host: String) = repository.saveHost(host)
}
