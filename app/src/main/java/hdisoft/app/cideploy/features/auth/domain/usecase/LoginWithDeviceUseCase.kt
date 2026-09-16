package hdisoft.app.cideploy.features.auth.domain.usecase

import hdisoft.app.cideploy.features.auth.domain.model.User
import hdisoft.app.cideploy.features.auth.domain.repository.AuthRepository

class LoginWithDeviceUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(deviceId: String): User? {
        return repository.loginWithDevice(deviceId)
    }
}
