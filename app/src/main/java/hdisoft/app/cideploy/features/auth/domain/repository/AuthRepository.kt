package hdisoft.app.cideploy.features.auth.domain.repository

import hdisoft.app.cideploy.features.auth.domain.model.User

interface AuthRepository {
    suspend fun loginWithDevice(deviceId: String): User?
    fun getSavedUser(): User?
    fun saveUser(user: User)
    fun logout()
}
