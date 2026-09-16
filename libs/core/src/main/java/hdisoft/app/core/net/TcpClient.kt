package hdisoft.app.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal line-based TCP client. Connects to a fixed host:port, reads newline-delimited
 * text, and can send text back. Reconnects automatically after a disconnect unless
 * [autoReconnect] is false or [stop] is called.
 */
class TcpClient {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onLineReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isConnected: Boolean
        get() = socket?.let { it.isConnected && !it.isClosed } == true

    fun connect(
        ip: String,
        port: Int,
        connectTimeoutMs: Int = 5000,
        retryDelayMs: Long = 3000,
        autoReconnect: Boolean = true
    ) {
        stop()
        job = scope.launch {
            do {
                try {
                    val s = Socket()
                    s.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                    s.tcpNoDelay = true
                    socket = s
                    writer = PrintWriter(BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)), true)

                    onConnected?.invoke()

                    val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        line?.let { onLineReceived?.invoke(it) }
                    }
                } catch (e: Exception) {
                    onError?.invoke(e.message ?: "TCP connection error")
                } finally {
                    try { socket?.close() } catch (e: Exception) {}
                    socket = null
                    writer = null
                    onDisconnected?.invoke()
                }
                if (autoReconnect && isActive) delay(retryDelayMs)
            } while (autoReconnect && isActive)
        }
    }

    /** Writes [message] to the socket. Returns false (and forces a reconnect) if the write fails. */
    fun send(message: String): Boolean {
        val w = writer ?: return false
        return try {
            w.print(message)
            w.flush()
            if (w.checkError()) throw Exception("Socket write error")
            true
        } catch (e: Exception) {
            try { socket?.close() } catch (ex: Exception) {}
            false
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        writer = null
    }
}
