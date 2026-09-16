package hdisoft.app.logcat.data.repository

import android.content.Context
import hdisoft.app.core.utils.DeviceUtils
import hdisoft.app.core.utils.NetworkUtils
import hdisoft.app.logcat.di.LogcatServiceLocator
import hdisoft.app.logcat.data.datasource.LocalLogcatDataSource
import hdisoft.app.logcat.data.datasource.WebSocketDataSource
import hdisoft.app.logcat.domain.model.LogStreamConfig
import hdisoft.app.logcat.domain.repository.LogcatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

class LogcatRepositoryImpl(
    private val context: Context,
    private val webSocketDataSource: WebSocketDataSource,
    private val localLogcatDataSource: LocalLogcatDataSource
) : LogcatRepository {

    private val PREFS_NAME = "CI_Deploy_Prefs"
    private val KEY_STREAM_MODE = "stream_mode"
    private val KEY_STREAM_IP = "stream_ip"
    private val KEY_STREAM_PORT = "stream_port"

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var streamJob: Job? = null
    private var logJob: Job? = null
    private var logProcess: Process? = null
    private var isStreaming = false

    override fun getStreamConfig(): LogStreamConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_STREAM_MODE, "SERVER")
        val mode = if (modeStr == "CLIENT") LogStreamConfig.Mode.CLIENT else LogStreamConfig.Mode.SERVER
        val ip = prefs.getString(KEY_STREAM_IP, "192.168.1.100") ?: "192.168.1.100"
        val port = prefs.getInt(KEY_STREAM_PORT, 8082)
        val protocolStr = prefs.getString("stream_protocol", "WEBSOCKET") ?: "WEBSOCKET"
        val protocol = try {
            LogStreamConfig.Protocol.valueOf(protocolStr)
        } catch (e: Exception) {
            LogStreamConfig.Protocol.WEBSOCKET
        }
        return LogStreamConfig(mode, ip, port, protocol)
    }

    override fun saveStreamConfig(config: LogStreamConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_STREAM_MODE, config.mode.name)
            putString(KEY_STREAM_IP, config.ip)
            putInt(KEY_STREAM_PORT, config.port)
            putString("stream_protocol", config.protocol.name)
            apply()
        }
    }

    private suspend fun verifyLogcatServer(ip: String, port: Int, protocol: LogStreamConfig.Protocol): Boolean = withContext(Dispatchers.IO) {
        if (protocol == LogStreamConfig.Protocol.UDP) {
            // UDP is connectionless, so a real check needs to send a REGISTER probe and wait
            // for the peer's welcome reply on the actual logcat port (see SocketStreamerImpl's
            // UDP server). Checking an unrelated TCP port (e.g. the CI build server's) previously
            // caused false positives that pointed the client at the wrong host.
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 400
                val target = InetSocketAddress(ip, port)
                val probe = "REGISTER".toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(probe, probe.size, target))

                val buffer = ByteArray(1024)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                response.address.hostAddress == ip
            } catch (e: Exception) {
                false
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        } else {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 400) // 400ms timeout
                true
            } catch (e: Exception) {
                false
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }
    }

    private suspend fun discoverLogcatServer(subnet: String, port: Int, protocol: LogStreamConfig.Protocol): String? = withContext(Dispatchers.IO) {
        val channel = Channel<String?>(Channel.CONFLATED)
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val semaphore = Semaphore(30)

        for (i in 1..254) {
            scope.launch {
                semaphore.withPermit {
                    val ip = "$subnet$i"
                    if (verifyLogcatServer(ip, port, protocol)) {
                        channel.send(ip)
                        job.cancel() // Cancel all other scanning tasks immediately
                    }
                }
            }
        }

        var result: String? = null
        try {
            withTimeout(4000) { // 4 seconds timeout for LAN scanning
                result = channel.receive()
            }
        } catch (e: Exception) {
            // Timeout or cancellation
        } finally {
            job.cancel()
        }
        result
    }

    private fun CoroutineScope.startLocalLogcatReading(config: LogStreamConfig, handleLogReceived: (String) -> Unit) {
        // Errors here must reach both the local screen and remote SERVER/CLIENT viewers,
        // otherwise a failed logcat process (e.g. su denied) looks like "connected but no logs".
        fun reportStreamError(message: String) {
            val sysMessage = "[SYSTEM] $message"
            if (config.mode == LogStreamConfig.Mode.SERVER || config.mode == LogStreamConfig.Mode.CLIENT) {
                if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
                    webSocketDataSource.sendLog(sysMessage)
                } else {
                    LogcatServiceLocator.externalLogStreamer?.sendLog(sysMessage)
                }
            }
            handleLogReceived(sysMessage)
        }

        logJob = launch(Dispatchers.IO) {
            var reader: BufferedReader? = null
            try {
                logProcess = localLogcatDataSource.getLogcatProcess()
                if (logProcess != null) {
                    reader = BufferedReader(InputStreamReader(logProcess!!.inputStream))
                    var line: String? = null

                    val pendingLogs = mutableListOf<String>()
                    var lastUpdateTime = System.currentTimeMillis()

                    while (isActive && reader.readLine().also { line = it } != null) {
                        line?.let { logLine ->
                            pendingLogs.add(logLine)
                            if (config.mode == LogStreamConfig.Mode.SERVER || config.mode == LogStreamConfig.Mode.CLIENT) {
                                if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
                                    webSocketDataSource.sendLog(logLine)
                                } else {
                                    LogcatServiceLocator.externalLogStreamer?.sendLog(logLine)
                                }
                            }
                        }

                        val currentTime = System.currentTimeMillis()
                        if (pendingLogs.size >= 100 || (currentTime - lastUpdateTime >= 100 && pendingLogs.isNotEmpty())) {
                            val logsChunk = pendingLogs.toList()
                            pendingLogs.clear()
                            lastUpdateTime = currentTime

                            withContext(Dispatchers.Main) {
                                logsChunk.forEach { handleLogReceived(it) }
                            }
                        }
                    }

                    if (pendingLogs.isNotEmpty()) {
                        val logsChunk = pendingLogs.toList()
                        withContext(Dispatchers.Main) {
                            logsChunk.forEach { handleLogReceived(it) }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        reportStreamError("Failed to start logcat process (check root/su access).")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    reportStreamError("Error reading real-time logcat: ${e.message}")
                }
            } finally {
                try { reader?.close() } catch (e: Exception) {}
                try { logProcess?.destroy() } catch (e: Exception) {}
                logProcess = null
            }
        }
    }

    override fun startStream(onLogReceived: (String) -> Unit, onStatusChanged: (String) -> Unit) {
        if (isStreaming) return
        isStreaming = true

        val config = getStreamConfig()
        val localIp = NetworkUtils.getLocalIpAddress(context) ?: "127.0.0.1"

        // Helper function to handle logs received (we wrap it to support custom behavior)
        val handleLogReceived: (String) -> Unit = { logLine ->
            // In CLIENT and SERVER modes, we do NOT display actual logcat on device to avoid lag and save resources.
            // We only display messages that come from the server (e.g. [SYSTEM] greetings or ping messages)
            if (config.mode == LogStreamConfig.Mode.CLIENT || config.mode == LogStreamConfig.Mode.SERVER) {
                if (logLine.startsWith("[SYSTEM]")) {
                    onLogReceived(logLine)
                }
            } else {
                // For DIRECT mode, display all logcat logs
                onLogReceived(logLine)
            }
        }

        val handleStatusChanged: (String) -> Unit = { status ->
            onStatusChanged(status)
            // If in CLIENT or SERVER mode, log connection status events directly onto the screen
            if (config.mode == LogStreamConfig.Mode.CLIENT || config.mode == LogStreamConfig.Mode.SERVER) {
                onLogReceived("[SYSTEM] $status")
            }
        }

        if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
            webSocketDataSource.onLogReceivedListener = handleLogReceived
            webSocketDataSource.onStatusChangedListener = handleStatusChanged
        } else {
            LogcatServiceLocator.externalLogStreamer?.let { streamer ->
                streamer.onLogReceivedListener = handleLogReceived
                streamer.onStatusChangedListener = handleStatusChanged
            }
        }

        streamJob = repositoryScope.launch {
            if (config.mode == LogStreamConfig.Mode.DIRECT) {
                handleStatusChanged("Direct View Mode | Viewing local logcat")
                startLocalLogcatReading(config, handleLogReceived)
                return@launch
            }

            if (config.mode == LogStreamConfig.Mode.SERVER) {
                if (DeviceUtils.isEmulator()) {
                    // Emulators sit behind their own virtual NAT (e.g. AVD's 10.0.2.15):
                    // inbound connections from a real device on the host's LAN never reach
                    // that address without `adb forward`/`adb reverse` port mapping run on
                    // the PC first. Outbound connections (CLIENT mode) are unaffected.
                    handleLogReceived(
                        "[SYSTEM] Warning: this is an emulator. Its IP ($localIp) is NOT reachable " +
                            "from other devices on the LAN — inbound connections need `adb forward tcp:${config.port} tcp:${config.port}` " +
                            "run on the PC, and the other device must connect to the PC's LAN IP, not $localIp. " +
                            "Easier: run Server Mode on the physical device instead and use Client Mode here."
                    )
                }
                if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
                    webSocketDataSource.start(config.mode, config.port, config.ip, localIp)
                } else {
                    LogcatServiceLocator.externalLogStreamer?.start(config.mode, config.protocol, config.ip, config.port, localIp)
                }
                startLocalLogcatReading(config, handleLogReceived)
                return@launch
            }

            // CLIENT mode: never trust a saved IP long-term. Every (re)connect attempt
            // re-verifies it and, on failure, re-scans the CURRENT local subnet — so
            // switching to a different LAN is picked up automatically instead of the
            // client getting stuck forever checking a stale IP from a previous network.
            // Note: "ci_deploy_host" (saved by Host Discovery) is the CI build server's IP,
            // used for OTA update checks on a different port. It is NOT another device
            // broadcasting logcat, so it must never be tried as a logcat server candidate here.
            startLocalLogcatReading(config, handleLogReceived)

            while (isActive) {
                var targetIp = getStreamConfig().ip
                handleStatusChanged("Checking connection to $targetIp:${config.port}...")
                var serverFound = verifyLogcatServer(targetIp, config.port, config.protocol)

                if (!serverFound) {
                    // Recomputed fresh on every retry (never cached) so a LAN switch is
                    // reflected immediately in which subnet actually gets scanned.
                    val subnet = NetworkUtils.getLocalSubnet(context)
                    handleStatusChanged("Scanning LAN ${subnet ?: "?"}0-254 for Logcat Server (port ${config.port})...")
                    if (subnet != null) {
                        val discoveredIp = discoverLogcatServer(subnet, config.port, config.protocol)
                        if (discoveredIp != null) {
                            targetIp = discoveredIp
                            saveStreamConfig(LogStreamConfig(config.mode, targetIp, config.port, config.protocol))
                            serverFound = true
                        }
                    } else {
                        handleStatusChanged("Error: Connect to Wi-Fi to scan local network.")
                    }
                }

                if (!serverFound) {
                    handleStatusChanged("Logcat Server (port ${config.port}) not found. Retrying in 10s...")
                    delay(10_000)
                    continue
                }

                if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
                    webSocketDataSource.start(config.mode, config.port, targetIp, localIp)
                } else {
                    LogcatServiceLocator.externalLogStreamer?.start(config.mode, config.protocol, targetIp, config.port, localIp)
                }

                // Hold this connection until it drops (e.g. the phone roams to a
                // different LAN), then loop back to re-verify/re-scan for the server's
                // new address instead of retrying the same now-stale IP forever.
                while (isActive) {
                    delay(5_000)
                    if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET && !webSocketDataSource.isClientConnected()) {
                        break
                    }
                }
            }
        }
    }

    override fun stopStream() {
        if (!isStreaming) return
        isStreaming = false

        streamJob?.cancel()
        streamJob = null

        logJob?.cancel()
        logJob = null

        repositoryScope.launch(Dispatchers.IO) {
            try {
                logProcess?.destroy()
            } catch (e: Exception) {}
            logProcess = null
        }

        val config = getStreamConfig()
        if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
            webSocketDataSource.stop()
        } else {
            LogcatServiceLocator.externalLogStreamer?.stop()
        }
    }

    override fun isStreamingActive(): Boolean = isStreaming

    override fun getStreamStatusText(): String {
        val config = getStreamConfig()
        return if (config.protocol == LogStreamConfig.Protocol.WEBSOCKET) {
            webSocketDataSource.getStatus()
        } else {
            LogcatServiceLocator.externalLogStreamer?.getStatus() ?: "External Streamer not available"
        }
    }

    override suspend fun clearLogs(wasActive: Boolean): Boolean {
        stopStream()
        val success = localLogcatDataSource.clearLogcatBuffer()
        return success
    }

    override suspend fun exportLogs(logs: List<String>, cacheDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(cacheDir, "ci_deploy_logcat.txt")
            file.writeText(logs.joinToString("\n"))
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
