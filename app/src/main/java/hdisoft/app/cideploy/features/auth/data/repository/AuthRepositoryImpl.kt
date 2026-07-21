package hdisoft.app.cideploy.features.auth.data.repository

import hdisoft.app.cideploy.features.auth.data.datasource.AuthLocalDataSource
import hdisoft.app.cideploy.features.auth.data.datasource.AuthRemoteDataSource
import hdisoft.app.cideploy.features.auth.domain.model.User
import hdisoft.app.cideploy.features.auth.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val localDataSource: AuthLocalDataSource,
    private val remoteDataSource: AuthRemoteDataSource
) : AuthRepository {

    override suspend fun loginWithDevice(deviceId: String): User? {
        return try {
            val user = remoteDataSource.loginWithDevice(deviceId)
            saveUser(user)
            user
        } catch (e: Exception) {
            null
        }
    }

    override fun getSavedUser(): User? {
        return localDataSource.getSavedUser()
    }

    override fun saveUser(user: User) {
        localDataSource.saveUser(user)
    }

    override fun logout() {
        localDataSource.clearUser()
    }
}
