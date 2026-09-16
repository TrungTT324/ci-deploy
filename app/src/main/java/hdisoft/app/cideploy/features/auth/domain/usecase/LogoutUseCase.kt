package hdisoft.app.cideploy.features.auth.domain.usecase

import hdisoft.app.cideploy.features.auth.domain.repository.AuthRepository

class LogoutUseCase(private val repository: AuthRepository) {
    operator fun invoke() {
        repository.logout()
    }
}
