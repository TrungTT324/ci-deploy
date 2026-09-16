package hdisoft.app.cideploy.features.auth.domain.usecase

import hdisoft.app.cideploy.features.auth.domain.model.User
import hdisoft.app.cideploy.features.auth.domain.repository.AuthRepository

class GetSavedUserUseCase(private val repository: AuthRepository) {
    operator fun invoke(): User? {
        return repository.getSavedUser()
    }
}
