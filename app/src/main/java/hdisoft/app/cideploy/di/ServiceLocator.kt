package hdisoft.app.cideploy.di

import android.content.Context
import hdisoft.app.appupdate.AppUpdateChecker
import hdisoft.app.appupdate.AppUpdateSettings

import hdisoft.app.cideploy.features.auth.data.datasource.AuthLocalDataSource
import hdisoft.app.cideploy.features.auth.data.datasource.AuthRemoteDataSource
import hdisoft.app.cideploy.features.auth.data.repository.AuthRepositoryImpl
import hdisoft.app.cideploy.features.auth.domain.repository.AuthRepository
import hdisoft.app.cideploy.features.auth.domain.usecase.*

object ServiceLocator {

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val context: Context
        get() = appContext ?: throw IllegalStateException("ServiceLocator has not been initialized. Call init(context) first.")



    // --- App Update Feature ---
    val appUpdateChecker: AppUpdateChecker by lazy { AppUpdateChecker(context) }

    // Passthrough so context-free classes (e.g. MainViewModel) can still
    // read/write this appupdate-owned setting without holding a Context.
    fun getUpdateSourceMode(): AppUpdateSettings.UpdateSourceMode =
        AppUpdateSettings.getUpdateSourceMode(context)

    // --- Auth Feature ---
    private val authLocalDataSource by lazy { AuthLocalDataSource(context) }
    private val authRemoteDataSource by lazy { AuthRemoteDataSource() }
    
    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authLocalDataSource, authRemoteDataSource)
    }

    val loginWithDeviceUseCase by lazy { LoginWithDeviceUseCase(authRepository) }
    val getSavedUserUseCase by lazy { GetSavedUserUseCase(authRepository) }
    val logoutUseCase by lazy { LogoutUseCase(authRepository) }
}
