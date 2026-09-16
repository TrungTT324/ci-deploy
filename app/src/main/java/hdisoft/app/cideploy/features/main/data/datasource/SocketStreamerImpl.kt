package hdisoft.app.cideploy.features.main.data.datasource

import hdisoft.app.core.net.TcpClient
import hdisoft.app.core.net.TcpServer
import hdisoft.app.logcat.domain.model.LogStreamConfig
import hdisoft.app.logcat.domain.repository.ExternalLogStreamer
import kotlinx.coroutines.*
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class SocketStreamerImpl : ExternalLogStreamer {

    private var mode = LogStreamConfig.Mode.DIRECT
    private var protocol = LogStreamConfig.Protocol.WEBSOCKET
    private var ip = "127.0.0.1"
    private var port = 8082
    private var localIp = "127.0.0.1"

    override var onLogReceivedListener: ((String) -> Unit)? = null
    override var onStatusChangedListener: ((String) -> Unit)? = null

    private val tcpServer = TcpServer()
    private val tcpClient = TcpClient()

    // For UDP Server
    private var udpServerSocket: DatagramSocket? = null
    private val udpClients = ConcurrentHashMap.newKeySet<SocketAddress>()
    private var udpServerJob: Job? = null

    // For UDP Client
    private var udpClientSocket: DatagramSocket? = null
    private var udpClientAddress: InetSocketAddress? = null

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Synchronized
    override fun start(mode: LogStreamConfig.Mode, protocol: LogStreamConfig.Protocol, ip: String, port: Int, localIp: String) {
        stop()
        this.mode = mode
        this.protocol = protocol
        this.ip = ip
        this.port = port
        this.localIp = localIp

        when (protocol) {
            LogStreamConfig.Protocol.TCP -> {
                if (mode == LogStreamConfig.Mode.SERVER) {
                    startTcpServer()
                } else if (mode == LogStreamConfig.Mode.CLIENT) {
                    startTcpClient()
                }
            }
            LogStreamConfig.Protocol.UDP -> {
                if (mode == LogStreamConfig.Mode.SERVER) {
                    startUdpServer()
                } else if (mode == LogStreamConfig.Mode.CLIENT) {
                    startUdpClient()
                }
            }
            else -> {
                onStatusChangedListener?.invoke("Unsupported external protocol: $protocol")
            }
        }
    }

    @Synchronized
    override fun stop() {
        tcpServer.stop()
        tcpClient.stop()

        // Stop UDP Server
        try { udpServerSocket?.close() } catch (e: Exception) {}
        udpServerSocket = null
        udpServerJob?.cancel()
        udpServerJob = null
        udpClients.clear()

        // Stop UDP Client
        try { udpClientSocket?.close() } catch (e: Exception) {}
        udpClientSocket = null
        udpClientAddress = null

        onStatusChangedListener?.invoke("Log streaming stopped")
    }

    override fun sendLog(log: String) {
        ioScope.launch {
            val logWithNewline = log + "\n"
            when (protocol) {
                LogStreamConfig.Protocol.TCP -> {
                    if (mode == LogStreamConfig.Mode.SERVER) {
                        tcpServer.broadcast(logWithNewline)
                        notifyStatusChanged()
                    } else if (mode == LogStreamConfig.Mode.CLIENT) {
                        tcpClient.send(logWithNewline)
                    }
                }
                LogStreamConfig.Protocol.UDP -> {
                    val bytes = logWithNewline.toByteArray(Charsets.UTF_8)
                    if (mode == LogStreamConfig.Mode.SERVER) {
                        val socket = udpServerSocket
                        if (socket != null && !socket.isClosed) {
                            for (addr in udpClients) {
                                try {
                                    val packet = DatagramPacket(bytes, bytes.size, addr)
                                    socket.send(packet)
                                } catch (e: Exception) {
                                    // Remove client if send fails persistently
                                    udpClients.remove(addr)
                                    notifyStatusChanged()
                                }
                            }
                        }
                    } else if (mode == LogStreamConfig.Mode.CLIENT) {
                        val socket = udpClientSocket
                        val addr = udpClientAddress
                        if (socket != null && !socket.isClosed && addr != null) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, addr)
                                socket.send(packet)
                            } catch (e: Exception) {
                                // UDP is connectionless
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    override fun getStatus(): String {
        return when (mode) {
            LogStreamConfig.Mode.DIRECT -> "Direct View Mode"
            LogStreamConfig.Mode.SERVER -> {
                val clientCount = if (protocol == LogStreamConfig.Protocol.TCP) tcpServer.clientCount else udpClients.size
                val scheme = if (protocol == LogStreamConfig.Protocol.TCP) "tcp" else "udp"
                "Server Mode | $scheme://$localIp:$port | Clients: $clientCount"
            }
            LogStreamConfig.Mode.CLIENT -> {
                val isConnected = when (protocol) {
                    LogStreamConfig.Protocol.TCP -> tcpClient.isConnected
                    LogStreamConfig.Protocol.UDP -> udpClientSocket != null && !udpClientSocket!!.isClosed
                    else -> false
                }
                val protocolName = protocol.name
                if (isConnected) "Client Mode | $protocolName | Connected to $ip:$port" else "Client Mode | $protocolName | Connecting to $ip:$port..."
            }
        }
    }

    private fun notifyStatusChanged() {
        onStatusChangedListener?.invoke(getStatus())
    }

    // --- TCP Server Implementation ---
    private fun startTcpServer() {
        onStatusChangedListener?.invoke("Starting TCP Server on port $port...")
        tcpServer.onClientConnected = { notifyStatusChanged() }
        tcpServer.onClientDisconnected = { notifyStatusChanged() }
        tcpServer.onLineReceived = { line -> onLogReceivedListener?.invoke(line) }
        tcpServer.onError = { msg -> onStatusChangedListener?.invoke("TCP Server Error: $msg") }
        tcpServer.start(port, welcomeMessage = "[SYSTEM]: Connected to CI-Deploy TCP Logcat Server\n")
        notifyStatusChanged()
    }

    // --- TCP Client Implementation ---
    private fun startTcpClient() {
        onStatusChangedListener?.invoke("Connecting to TCP Server: tcp://$ip:$port...")
        tcpClient.onConnected = {
            notifyStatusChanged()
            tcpClient.send("[SYSTEM]: Device ${android.os.Build.MODEL} connected as log producer via TCP\n")
        }
        tcpClient.onDisconnected = { notifyStatusChanged() }
        tcpClient.onLineReceived = { line -> onLogReceivedListener?.invoke(line) }
        tcpClient.onError = { msg -> onStatusChangedListener?.invoke("TCP connection error: $msg. Reconnecting...") }
        tcpClient.connect(ip, port)
    }

    // --- UDP Server Implementation ---
    private fun startUdpServer() {
        onStatusChangedListener?.invoke("Starting UDP Server on port $port...")
        udpServerJob = ioScope.launch {
            try {
                val socket = DatagramSocket(port)
                udpServerSocket = socket
                notifyStatusChanged()

                val buffer = ByteArray(1024)
                while (isActive && !socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val senderAddress = packet.socketAddress
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()

                    if (message.startsWith("REGISTER") || message.startsWith("CONNECT")) {
                        if (udpClients.add(senderAddress)) {
                            notifyStatusChanged()
                            // Send welcome packet
                            val welcome = "[SYSTEM]: Connected to CI-Deploy UDP Logcat Server\n".toByteArray(Charsets.UTF_8)
                            socket.send(DatagramPacket(welcome, welcome.size, senderAddress))
                        }
                    } else if (message.startsWith("UNREGISTER") || message.startsWith("DISCONNECT")) {
                        if (udpClients.remove(senderAddress)) {
                            notifyStatusChanged()
                        }
                    } else {
                        // Received general log/message from client
                        onLogReceivedListener?.invoke(message)
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    onStatusChangedListener?.invoke("UDP Server Error: ${e.message}")
                }
            }
        }
    }

    // --- UDP Client Implementation ---
    private fun startUdpClient() {
        onStatusChangedListener?.invoke("Setting up UDP Client to: udp://$ip:$port...")
        ioScope.launch {
            try {
                val socket = DatagramSocket()
                udpClientSocket = socket
                udpClientAddress = InetSocketAddress(ip, port)
                notifyStatusChanged()

                // Send a register message to let the server know we exist
                val registerMsg = "REGISTER".toByteArray(Charsets.UTF_8)
                socket.send(DatagramPacket(registerMsg, registerMsg.size, udpClientAddress))

                // Start a receive loop in case the server replies
                val buffer = ByteArray(1024)
                while (isActive && !socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    onLogReceivedListener?.invoke(message)
                }
            } catch (e: Exception) {
                if (isActive) {
                    onStatusChangedListener?.invoke("UDP Client Error: ${e.message}")
                }
            }
        }
    }
}
