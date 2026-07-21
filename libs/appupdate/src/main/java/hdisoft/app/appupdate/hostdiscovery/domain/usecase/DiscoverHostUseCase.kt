package hdisoft.app.appupdate.hostdiscovery.domain.usecase

import hdisoft.app.appupdate.hostdiscovery.domain.repository.HostRepository

class DiscoverHostUseCase(private val repository: HostRepository) {
    suspend operator fun invoke(subnet: String): String? = repository.discoverHost(subnet)
}
