package hdisoft.app.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Minimal line-based TCP server: accepts multiple clients, reads newline-delimited
 * text from each, and can broadcast a message to all connected clients.
 */
class TcpServer {
    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Socket>()
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onClientConnected: ((Socket) -> Unit)? = null
    var onClientDisconnected: ((Socket) -> Unit)? = null
    var onLineReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val clientCount: Int get() = clients.size
    val isRunning: Boolean get() = serverSocket != null && serverSocket?.isClosed == false

    fun start(port: Int, welcomeMessage: String? = null) {
        stop()
        serverJob = scope.launch {
            try {
                val server = ServerSocket(port).apply { reuseAddress = true }
                serverSocket = server

                while (isActive && !server.isClosed) {
                    val client = server.accept()
                    client.tcpNoDelay = true
                    clients.add(client)

                    if (welcomeMessage != null) {
                        try {
                            client.getOutputStream().write(welcomeMessage.toByteArray(Charsets.UTF_8))
                        } catch (e: Exception) { /* client dropped before welcome could be sent */ }
                    }
                    onClientConnected?.invoke(client)

                    launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                            var line: String? = null
                            while (isActive && reader.readLine().also { line = it } != null) {
                                line?.let { onLineReceived?.invoke(it) }
                            }
                        } catch (e: Exception) {
                            // client disconnected
                        } finally {
                            clients.remove(client)
                            try { client.close() } catch (ex: Exception) {}
                            onClientDisconnected?.invoke(client)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) onError?.invoke(e.message ?: "TCP server error")
            }
        }
    }

    /** Sends [message] to every connected client; drops and closes any client whose write fails. */
    fun broadcast(message: String) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        val deadClients = mutableListOf<Socket>()
        for (client in clients) {
            try {
                val out = client.getOutputStream()
                out.write(bytes)
                out.flush()
            } catch (e: Exception) {
                deadClients.add(client)
            }
        }
        if (deadClients.isNotEmpty()) {
            clients.removeAll(deadClients.toSet())
            for (client in deadClients) {
                try { client.close() } catch (ex: Exception) {}
                onClientDisconnected?.invoke(client)
            }
        }
    }

    fun stop() {
        try { serverSocket?.close() } catch (e: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        for (client in clients) {
            try { client.close() } catch (e: Exception) {}
        }
        clients.clear()
    }
}
