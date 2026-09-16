package hdisoft.app.appupdate.hostdiscovery.presentation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import hdisoft.app.appupdate.AppUpdateSettings
import hdisoft.app.appupdate.hostdiscovery.di.HostDiscoveryServiceLocator
import hdisoft.app.core.utils.NetworkUtils
import kotlinx.coroutines.*

/**
 * LAN host resolution. [startDiscovery] always tries the last known host
 * first, regardless of [AppUpdateSettings.getUpdateSourceMode] — only if
 * that fails does the mode matter: [AppUpdateSettings.UpdateSourceMode.LAN_ONLY]
 * falls back to a full subnet scan, `PUBLIC_HOST` reports [State.Error]
 * immediately (there's nothing else to try). Safe to call repeatedly —
 * it's a no-op while already in [State.Progress].
 */
class HostDiscoveryService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var getSavedHostUseCase: hdisoft.app.appupdate.hostdiscovery.domain.usecase.GetSavedHostUseCase
    private lateinit var saveHostUseCase: hdisoft.app.appupdate.hostdiscovery.domain.usecase.SaveHostUseCase
    private lateinit var verifyHostUseCase: hdisoft.app.appupdate.hostdiscovery.domain.usecase.VerifyHostUseCase
    private lateinit var discoverHostUseCase: hdisoft.app.appupdate.hostdiscovery.domain.usecase.DiscoverHostUseCase

    var onStateChangedListener: ((State) -> Unit)? = null
    private var currentState: State = State.Idle
        set(value) {
            field = value
            onStateChangedListener?.invoke(value)
        }

    sealed class State {
        object Idle : State()
        data class Progress(val message: String) : State()
        data class Success(val host: String) : State()
        data class Error(val error: String) : State()
    }

    inner class LocalBinder : Binder() {
        fun getService(): HostDiscoveryService = this@HostDiscoveryService
    }

    override fun onCreate() {
        super.onCreate()
        HostDiscoveryServiceLocator.init(this)
        getSavedHostUseCase = HostDiscoveryServiceLocator.getSavedHostUseCase
        saveHostUseCase = HostDiscoveryServiceLocator.saveHostUseCase
        verifyHostUseCase = HostDiscoveryServiceLocator.verifyHostUseCase
        discoverHostUseCase = HostDiscoveryServiceLocator.discoverHostUseCase
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    fun getServiceState(): State = currentState

    fun startDiscovery() {
        if (currentState is State.Progress) return

        serviceScope.launch {
            currentState = State.Progress("Checking connection...")
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager

            if (!wifiManager.isWifiEnabled) {
                currentState = State.Progress("Enabling Wi-Fi...")
                NetworkUtils.ensureWifiEnabled(this@HostDiscoveryService)
            }

            var attempts = 0
            val maxAttempts = 15
            var currentIp = NetworkUtils.getLocalIpAddress(this@HostDiscoveryService)

            while ((!wifiManager.isWifiEnabled || currentIp == null) && attempts < maxAttempts) {
                if (currentIp != null) {
                    break
                }
                currentState = State.Progress("Waiting for Wi-Fi and IP address (${attempts + 1}/${maxAttempts}s)...")
                delay(1000)
                currentIp = NetworkUtils.getLocalIpAddress(this@HostDiscoveryService)
                attempts++
            }

            if (currentIp == null) {
                currentState = State.Error("No IP address found. Please connect to a Wi-Fi network.")
                stopSelf()
                return@launch
            }

            // Phase 1: always try the last known-good host first, regardless of source mode.
            val savedHost = getSavedHostUseCase()
            if (savedHost != null) {
                currentState = State.Progress("Local IP: $currentIp | Checking last host: $savedHost...")
                if (verifyHostUseCase(savedHost)) {
                    currentState = State.Success(savedHost)
                    stopSelf()
                    return@launch
                }
            }

            // Phase 2: last host is gone or unreachable — only LAN_ONLY may scan for a new one.
            if (AppUpdateSettings.getUpdateSourceMode(applicationContext) != AppUpdateSettings.UpdateSourceMode.LAN_ONLY) {
                currentState = State.Error(
                    if (savedHost != null) {
                        "Cannot reach $savedHost, and LAN discovery is off (Public host mode)."
                    } else {
                        "No known host, and LAN discovery is off (Public host mode)."
                    }
                )
                stopSelf()
                return@launch
            }

            scanLocalSubnet()
        }
    }

    private suspend fun scanLocalSubnet() {
        val maxRetries = 3
        var retryCount = 1

        while (retryCount <= maxRetries) {
            if (retryCount > 1) {
                for (sec in 5 downTo 1) {
                    currentState = State.Progress("No host found. Retry $retryCount/$maxRetries in ${sec}s...")
                    delay(1000)
                }
            }

            val localIp = NetworkUtils.getLocalIpAddress(this@HostDiscoveryService) ?: "unknown"
            currentState = State.Progress("Local IP: $localIp | Scanning local network (Attempt $retryCount/$maxRetries)...")

            var subnet = NetworkUtils.getLocalSubnet(this@HostDiscoveryService)
            var subnetAttempts = 0
            while (subnet == null && subnetAttempts < 5) {
                delay(1000)
                subnet = NetworkUtils.getLocalSubnet(this@HostDiscoveryService)
                subnetAttempts++
            }

            if (subnet == null) {
                if (retryCount >= maxRetries) {
                    currentState = State.Error("Cannot determine local IP subnet. Connect to Wi-Fi.")
                    stopSelf()
                    return
                }
                retryCount++
                continue
            }

            try {
                val discovered = discoverHostUseCase(subnet)
                if (discovered != null) {
                    saveHostUseCase(discovered)
                    currentState = State.Success(discovered)
                    stopSelf()
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("CI_DEPLOY_ROOT", "Scan error: ${e.message}", e)
            }

            if (retryCount >= maxRetries) {
                currentState = State.Error("No host found on port 8080 after $maxRetries attempts.")
                stopSelf()
                return
            }
            retryCount++
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
