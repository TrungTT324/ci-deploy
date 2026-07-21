package hdisoft.app.cideploy.features.auth.data.datasource

import hdisoft.app.cideploy.features.auth.domain.model.User
import kotlinx.coroutines.delay

class AuthRemoteDataSource {
    suspend fun loginWithDevice(deviceId: String): User {
        // Mock network delay (1.5 seconds)
        delay(1500)
        // Generate mock token
        val mockToken = "mock_jwt_token_for_$deviceId"
        val prefixLength = if (deviceId.length >= 8) 8 else deviceId.length
        val suffixLength = if (deviceId.length >= 6) 6 else deviceId.length
        return User(
            id = "usr_${deviceId.takeLast(suffixLength)}",
            username = "Device_${deviceId.take(prefixLength).uppercase()}",
            token = mockToken,
            deviceId = deviceId
        )
    }
}
