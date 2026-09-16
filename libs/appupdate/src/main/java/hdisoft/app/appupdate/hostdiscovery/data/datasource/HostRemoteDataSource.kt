package hdisoft.app.appupdate.hostdiscovery.data.datasource

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL

/**
 * [verifyPath] is checked as `http://<host>:[port]/[verifyPath]` — defaults
 * match CI-Deploy's own OTA JSON (`AppUpdateChecker.resolveJsonUrl` for
 * baseUrl `"http://<host>:8080/ci-deploy"`), since that's the only consumer
 * today; pass your own if you integrate this elsewhere.
 */
class HostRemoteDataSource(
    private val port: Int = 8080,
    private val verifyPath: String = "ci-deploy/ci-deploy-version.json"
) {

    suspend fun verifyHost(host: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Fast TCP Socket check on port 8080 with 500ms timeout
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), 500)
            }

            // Port is open, proceed to HTTP verification
            val url = URL("http://$host:$port/$verifyPath")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }

    suspend fun discoverHost(subnet: String): String? = withContext(Dispatchers.IO) {
        val channel = Channel<String?>(Channel.CONFLATED)
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val semaphore = Semaphore(80) // Increase concurrent tasks limit to 80

        for (i in 1..254) {
            scope.launch {
                semaphore.withPermit {
                    val host = "$subnet$i"
                    if (verifyHost(host)) {
                        channel.send(host)
                        job.cancel() // Cancel all other scanning tasks immediately
                    }
                }
            }
        }

        var result: String? = null
        try {
            // Wait up to 15 seconds for a response
            withTimeout(15000) {
                result = channel.receive()
            }
        } catch (e: Exception) {
            // Timeout or cancellation
        } finally {
            job.cancel()
        }
        result
    }
}
