package hdisoft.app.cideploy.features.main.presentation

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hdisoft.app.cideploy.di.ServiceLocator
import hdisoft.app.core.utils.NetworkUtils
import hdisoft.app.appupdate.AppUpdateChecker
import hdisoft.app.appupdate.AppUpdateSettings
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.InstallState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.cideploy.features.auth.domain.model.User
import hdisoft.app.core.utils.DeviceUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File

class MainViewModel : ViewModel() {

    private val appUpdateChecker: AppUpdateChecker = ServiceLocator.appUpdateChecker
    private val loginWithDeviceUseCase = ServiceLocator.loginWithDeviceUseCase
    private val getSavedUserUseCase = ServiceLocator.getSavedUserUseCase

    private val _currentHost = MutableLiveData<String?>()
    val currentHost: LiveData<String?> = _currentHost

    private val _isScanning = MutableLiveData<Boolean>(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _loadingMessage = MutableLiveData<String?>()
    val loadingMessage: LiveData<String?> = _loadingMessage

    private val _scanError = MutableLiveData<String?>()
    val scanError: LiveData<String?> = _scanError

    private val _updateInfo = MutableLiveData<UpdateInfo?>()
    val updateInfo: LiveData<UpdateInfo?> = _updateInfo

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _isAuthenticating = MutableLiveData<Boolean>(false)
    val isAuthenticating: LiveData<Boolean> = _isAuthenticating

    private var downloadJob: Job? = null
    private var checkUpdateJob: Job? = null
    private var pendingUpdateInfo: UpdateInfo? = null

    fun performSilentAuth(context: Context) {
        val saved = getSavedUserUseCase()
        if (saved != null) {
            _currentUser.value = saved
            return
        }
        
        _isAuthenticating.value = true
        viewModelScope.launch {
            val deviceId = DeviceUtils.getDeviceId(context)
            val user = loginWithDeviceUseCase(deviceId)
            _currentUser.value = user
            _isAuthenticating.value = false
        }
    }

    fun setHost(host: String) {
        _currentHost.value = host
        checkForUpdates(host)
        restartUpdateCheckTimer()
    }

    fun setLoadingMessage(msg: String?) {
        _loadingMessage.value = msg
    }

    fun setScanError(err: String?) {
        _scanError.value = err
    }

    fun setIsScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    private var dismissedBuildNo: Long = 0

    fun dismissVersion(buildNo: Long) {
        dismissedBuildNo = buildNo
        android.util.Log.d("CI_DEPLOY_ROOT", "Dismissed buildNo: $buildNo")
    }

    fun checkForUpdates(host: String) {
        if (_downloadState.value is DownloadState.Downloading ||
            downloadJob != null ||
            _installState.value is InstallState.Verifying ||
            _installState.value is InstallState.Installing
        ) {
            android.util.Log.d("CI_DEPLOY_ROOT", "checkForUpdates skipped: download or job is active.")
            return
        }
        if (checkUpdateJob?.isActive == true) return
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val baseUrl = when (ServiceLocator.getUpdateSourceMode()) {
                    AppUpdateSettings.UpdateSourceMode.PUBLIC_HOST -> AppUpdateSettings.DEFAULT_PUBLIC_BASE_URL
                    AppUpdateSettings.UpdateSourceMode.LAN_ONLY -> "http://$host:8080/ci-deploy"
                }
                val info = appUpdateChecker.checkUpdate(baseUrl)
                if (info != null) {
                    if (info.buildNo == dismissedBuildNo) {
                        android.util.Log.d("CI_DEPLOY_ROOT", "checkForUpdates: Build $dismissedBuildNo was dismissed/failed. Skipping notification.")
                        return@launch
                    }
                    _updateInfo.value = info
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (checkUpdateJob === job) checkUpdateJob = null
            }
        }
        checkUpdateJob = job
        job.start()
    }

    fun startDownload(info: UpdateInfo, url: String, targetFile: File) {
        if (downloadJob != null || _downloadState.value is DownloadState.Downloading) {
            android.util.Log.d("CI_DEPLOY_ROOT", "startDownload ignored: download already in progress.")
            return
        }
        pendingUpdateInfo = info.copy(url = url)
        _installState.value = InstallState.Idle
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                appUpdateChecker.downloadApk(url, targetFile).collect { state ->
                    _downloadState.value = state
                }
            } catch (e: CancellationException) {
                _downloadState.value = DownloadState.Cancelled
                throw e
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            } finally {
                if (downloadJob === job) downloadJob = null
            }
        }
        downloadJob = job
        job.start()
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Cancelled
        _installState.value = InstallState.Idle
    }

    @Synchronized
    private fun beginVerification(): UpdateInfo? {
        val info = pendingUpdateInfo ?: return null
        if (_installState.value !is InstallState.Idle) return null
        return info
    }

    /** Verifies + installs (silently via root when [autoInstallWithRoot]) via [AppUpdateChecker.installDownloadedApk]. */
    fun verifyDownloadedApk(context: Context, file: File, autoInstallWithRoot: Boolean) {
        val info = beginVerification() ?: return
        viewModelScope.launch {
            appUpdateChecker.installDownloadedApk(context, info, file, autoInstallIfRooted = autoInstallWithRoot)
                .collect { state ->
                    _installState.value = state
                    if (state is InstallState.Error) restartUpdateCheckTimer()
                }
        }
    }

    /** Opens the system installer for [file]; requests the install-unknown-apps permission first if missing. */
    fun installApk(context: Context, file: File): Boolean = appUpdateChecker.installApk(context, file)

    fun cleanupTempApkFiles(cacheDir: File) = appUpdateChecker.cleanupTempApkFiles(cacheDir)

    fun resetUpdateOperation() {
        pendingUpdateInfo = null
        _downloadState.value = DownloadState.Idle
        _installState.value = InstallState.Idle
        restartUpdateCheckTimer()
    }

    fun saveManualHost(context: Context, host: String) {
        hdisoft.app.appupdate.hostdiscovery.di.HostDiscoveryServiceLocator.init(context)
        hdisoft.app.appupdate.hostdiscovery.di.HostDiscoveryServiceLocator.saveHostUseCase(host)
        _currentHost.value = host
        checkForUpdates(host)
        restartUpdateCheckTimer()
    }

    fun clearScanError() {
        _scanError.value = null
    }

    fun clearUpdateInfo() {
        _updateInfo.value = null
    }

    fun resumePeriodicUpdateCheck() {
        restartUpdateCheckTimer()
    }

    private var updateCheckJob: Job? = null
    private val _checkInterval = MutableStateFlow<Int>(0)
    val checkInterval: StateFlow<Int> = _checkInterval

    fun setCheckInterval(intervalSeconds: Int) {
        _checkInterval.value = intervalSeconds
        restartUpdateCheckTimer()
    }

    fun startPeriodicUpdateCheck() {
        restartUpdateCheckTimer()
    }

    fun stopPeriodicUpdateCheck() {
        updateCheckJob?.cancel()
        updateCheckJob = null
    }

    private fun restartUpdateCheckTimer() {
        updateCheckJob?.cancel()
        val interval = _checkInterval.value
        val host = _currentHost.value
        val downloadState = _downloadState.value

        if (downloadState is DownloadState.Downloading || downloadState is DownloadState.Success) {
            updateCheckJob = null
            return
        }

        if (interval > 0 && host != null) {
            updateCheckJob = viewModelScope.launch {
                while (isActive) {
                    delay(interval * 1000L)
                    checkForUpdates(host)
                }
            }
        } else {
            updateCheckJob = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateCheckJob?.cancel()
        checkUpdateJob?.cancel()
        downloadJob?.cancel()
    }
}
