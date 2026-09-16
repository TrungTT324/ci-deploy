package hdisoft.app.logcat.data.datasource

import hdisoft.app.logcat.domain.model.LogStreamConfig
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.net.URI

class WebSocketDataSource {

    private var server: LogWebSocketServer? = null
    private var client: LogWebSocketClient? = null
    private var mode = LogStreamConfig.Mode.DIRECT
    private var port = 8082
    private var serverIp = "127.0.0.1"
    private var localIp = "127.0.0.1"

    var onLogReceivedListener: ((String) -> Unit)? = null
    var onStatusChangedListener: ((String) -> Unit)? = null

    fun start(mode: LogStreamConfig.Mode, port: Int, serverIp: String = "127.0.0.1", localIp: String = "127.0.0.1") {
        stop()
        this.mode = mode
        this.port = port
        this.serverIp = serverIp
        this.localIp = localIp

        if (mode == LogStreamConfig.Mode.SERVER) {
            onStatusChangedListener?.invoke("Starting WebSocket Server on port $port...")
            try {
                server = LogWebSocketServer(port).apply {
                    isReuseAddr = true
                    start()
                }
            } catch (e: Exception) {
                onStatusChangedListener?.invoke("Server Error: ${e.message}")
            }
        } else if (mode == LogStreamConfig.Mode.CLIENT) {
            val uriStr = "ws://$serverIp:$port"
            onStatusChangedListener?.invoke("Connecting to Server: $uriStr...")
            try {
                client = LogWebSocketClient(URI(uriStr)).apply {
                    connect()
                }
            } catch (e: Exception) {
                onStatusChangedListener?.invoke("Client connection error: ${e.message}")
            }
        } else {
            onStatusChangedListener?.invoke("Direct View Mode")
        }
    }

    fun stop() {
        try {
            server?.stop(1000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        server = null

        try {
            client?.closeBlocking()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        client = null
        onStatusChangedListener?.invoke("Log streaming stopped")
    }

    fun sendLog(log: String) {
        if (mode == LogStreamConfig.Mode.SERVER) {
            server?.broadcastLog(log)
        } else if (mode == LogStreamConfig.Mode.CLIENT) {
            if (client?.isOpen == true) {
                client?.send(log)
            }
        }
    }

    fun isClientConnected(): Boolean = mode == LogStreamConfig.Mode.CLIENT && client?.isOpen == true

    fun getStatus(): String {
        return when (mode) {
            LogStreamConfig.Mode.DIRECT -> "Direct View Mode"
            LogStreamConfig.Mode.SERVER -> {
                val clientCount = server?.getConnectionsCount() ?: 0
                "Server Mode | ws://$localIp:$port | Clients: $clientCount"
            }
            LogStreamConfig.Mode.CLIENT -> {
                val isConnected = client?.isOpen == true
                if (isConnected) "Client Mode | Connected to $serverIp:$port" else "Client Mode | Connecting to $serverIp:$port..."
            }
        }
    }

    // WebSocket Server Inner Class
    private inner class LogWebSocketServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {
        private val connectionsList = mutableSetOf<WebSocket>()

        fun getConnectionsCount(): Int = synchronized(connectionsList) { connectionsList.size }

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            synchronized(connectionsList) {
                connectionsList.add(conn)
            }
            conn.send("[SYSTEM]: Connected to CI-Deploy Logcat Server")
            onStatusChangedListener?.invoke(getStatus())
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            synchronized(connectionsList) {
                connectionsList.remove(conn)
            }
            onStatusChangedListener?.invoke(getStatus())
        }

        override fun onMessage(conn: WebSocket, message: String) {
            onLogReceivedListener?.invoke(message)
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            onStatusChangedListener?.invoke("Server Error: ${ex.message}")
        }

        override fun onStart() {
            onStatusChangedListener?.invoke(getStatus())
        }

        fun broadcastLog(log: String) {
            synchronized(connectionsList) {
                for (conn in connectionsList) {
                    if (conn.isOpen) {
                        conn.send(log)
                    }
                }
            }
        }
    }

    // WebSocket Client Inner Class
    private inner class LogWebSocketClient(serverUri: URI) : WebSocketClient(serverUri) {
        override fun onOpen(handshakedata: ServerHandshake) {
            onStatusChangedListener?.invoke(getStatus())
            send("[SYSTEM]: Device ${android.os.Build.MODEL} connected as log producer")
        }

        override fun onMessage(message: String) {
            onLogReceivedListener?.invoke(message)
        }

        override fun onClose(code: Int, reason: String, remote: Boolean) {
            onStatusChangedListener?.invoke(getStatus())
        }

        override fun onError(ex: Exception) {
            onStatusChangedListener?.invoke("Connection Error: ${ex.message}")
        }
    }
}
