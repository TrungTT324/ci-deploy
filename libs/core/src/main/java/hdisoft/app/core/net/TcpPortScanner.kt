package hdisoft.app.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.net.Socket

/** Scans a /24 subnet for the first host with a given TCP port open. */
object TcpPortScanner {

    /**
     * Tries every address in [subnet]1..254 (e.g. subnet = "192.168.1.") concurrently and
     * returns the first IP that accepts a TCP connection on [port], or null if none respond
     * within [timeoutMs].
     */
    suspend fun scan(
        subnet: String,
        port: Int,
        connectTimeoutMs: Int = 400,
        timeoutMs: Long = 4000,
        concurrency: Int = 30
    ): String? = withContext(Dispatchers.IO) {
        val channel = Channel<String?>(Channel.CONFLATED)
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val semaphore = Semaphore(concurrency)

        for (i in 1..254) {
            scope.launch {
                semaphore.withPermit {
                    val ip = "$subnet$i"
                    if (isPortOpen(ip, port, connectTimeoutMs)) {
                        channel.send(ip)
                        job.cancel()
                    }
                }
            }
        }

        var result: String? = null
        try {
            withTimeout(timeoutMs) {
                result = channel.receive()
            }
        } catch (e: Exception) {
            // timeout or cancelled — no host found
        } finally {
            job.cancel()
        }
        result
    }

    fun isPortOpen(ip: String, port: Int, connectTimeoutMs: Int = 400): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
