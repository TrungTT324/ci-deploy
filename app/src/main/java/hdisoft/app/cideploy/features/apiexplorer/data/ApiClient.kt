package hdisoft.app.cideploy.features.apiexplorer.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class ApiResult(
    val statusCode: Int,
    val body: String,
    val error: String? = null
) {
    val isSuccess: Boolean get() = error == null && statusCode in 200..299
}

object ApiClient {

    suspend fun execute(
        method: String,
        urlString: String,
        username: String,
        password: String,
        body: String?
    ): ApiResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (username.isNotEmpty() || password.isNotEmpty()) {
                val credentials = Base64.encodeToString(
                    "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP
                )
                connection.setRequestProperty("Authorization", "Basic $credentials")
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val statusCode = connection.responseCode
            val stream: InputStream? = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.let { readAll(it) } ?: ""

            ApiResult(statusCode = statusCode, body = responseBody)
        } catch (e: Exception) {
            ApiResult(statusCode = -1, body = "", error = e.message ?: e.toString())
        } finally {
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }

    private fun readAll(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
        val buffer = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            buffer.append(line).append("\n")
        }
        reader.close()
        return buffer.toString()
    }
}
