package hdisoft.app.cideploy.features.auth.domain.model

data class User(
    val id: String,
    val username: String,
    val token: String,
    val deviceId: String
)
