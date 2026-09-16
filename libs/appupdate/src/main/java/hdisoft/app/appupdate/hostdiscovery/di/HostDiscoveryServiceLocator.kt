package hdisoft.app.appupdate.hostdiscovery.di

import android.content.Context
import hdisoft.app.appupdate.hostdiscovery.data.datasource.HostLocalDataSource
import hdisoft.app.appupdate.hostdiscovery.data.datasource.HostRemoteDataSource
import hdisoft.app.appupdate.hostdiscovery.data.repository.HostRepositoryImpl
import hdisoft.app.appupdate.hostdiscovery.domain.repository.HostRepository
import hdisoft.app.appupdate.hostdiscovery.domain.usecase.DiscoverHostUseCase
import hdisoft.app.appupdate.hostdiscovery.domain.usecase.GetSavedHostUseCase
import hdisoft.app.appupdate.hostdiscovery.domain.usecase.SaveHostUseCase
import hdisoft.app.appupdate.hostdiscovery.domain.usecase.VerifyHostUseCase

object HostDiscoveryServiceLocator {

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() = appContext ?: throw IllegalStateException("HostDiscoveryServiceLocator has not been initialized. Call init(context) first.")

    private val hostLocalDataSource by lazy { HostLocalDataSource(context) }
    private val hostRemoteDataSource by lazy { HostRemoteDataSource() }

    val hostRepository: HostRepository by lazy {
        HostRepositoryImpl(hostLocalDataSource, hostRemoteDataSource)
    }

    val getSavedHostUseCase by lazy { GetSavedHostUseCase(hostRepository) }
    val saveHostUseCase by lazy { SaveHostUseCase(hostRepository) }
    val verifyHostUseCase by lazy { VerifyHostUseCase(hostRepository) }
    val discoverHostUseCase by lazy { DiscoverHostUseCase(hostRepository) }
}
