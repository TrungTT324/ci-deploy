package hdisoft.app.appupdate.hostdiscovery.domain.repository

interface HostRepository {
    fun getSavedHost(): String?
    fun saveHost(host: String)
    suspend fun verifyHost(host: String): Boolean
    suspend fun discoverHost(subnet: String): String?
}
