package hdisoft.app.appupdate.hostdiscovery.data.repository

import hdisoft.app.appupdate.hostdiscovery.data.datasource.HostLocalDataSource
import hdisoft.app.appupdate.hostdiscovery.data.datasource.HostRemoteDataSource
import hdisoft.app.appupdate.hostdiscovery.domain.repository.HostRepository

class HostRepositoryImpl(
    private val localDataSource: HostLocalDataSource,
    private val remoteDataSource: HostRemoteDataSource
) : HostRepository {

    override fun getSavedHost(): String? {
        return localDataSource.getSavedHost()
    }

    override fun saveHost(host: String) {
        localDataSource.saveHost(host)
    }

    override suspend fun verifyHost(host: String): Boolean {
        return remoteDataSource.verifyHost(host)
    }

    override suspend fun discoverHost(subnet: String): String? {
        return remoteDataSource.discoverHost(subnet)
    }
}
