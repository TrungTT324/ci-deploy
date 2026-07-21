package hdisoft.app.cidata.di

import android.content.Context
import hdisoft.app.cidata.data.datasource.CidataRemoteDataSource
import hdisoft.app.cidata.data.repository.CidataRepositoryImpl
import hdisoft.app.cidata.domain.repository.CidataRepository
import hdisoft.app.cidata.domain.usecase.DeviceCheckinUseCase
import hdisoft.app.core.utils.DeviceUtils

object CidataServiceLocator {

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private val context: Context
        get() = appContext ?: throw IllegalStateException("CidataServiceLocator has not been initialized. Call init(context) first.")

    /** Stable per-install device id, reused from :libs:core (Settings.Secure.ANDROID_ID). */
    val deviceUid: String
        get() = DeviceUtils.getDeviceId(context)

    private val cidataRemoteDataSource by lazy { CidataRemoteDataSource() }

    val cidataRepository: CidataRepository by lazy {
        CidataRepositoryImpl(cidataRemoteDataSource)
    }

    val deviceCheckinUseCase by lazy { DeviceCheckinUseCase(cidataRepository) }
}
