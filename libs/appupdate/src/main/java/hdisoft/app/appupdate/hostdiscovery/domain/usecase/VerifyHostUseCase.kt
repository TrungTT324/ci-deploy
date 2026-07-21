package hdisoft.app.appupdate.hostdiscovery.domain.usecase

import hdisoft.app.appupdate.hostdiscovery.domain.repository.HostRepository

class VerifyHostUseCase(private val repository: HostRepository) {
    suspend operator fun invoke(host: String): Boolean = repository.verifyHost(host)
}
