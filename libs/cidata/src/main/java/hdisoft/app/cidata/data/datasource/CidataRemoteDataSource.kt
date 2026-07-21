package hdisoft.app.cidata.data.datasource

import hdisoft.app.cidata.domain.model.DeviceCheckinResult
import hdisoft.app.cidata.domain.model.LatestRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class CidataRemoteDataSource {

    suspend fun checkin(
        host: String,
        port: Int,
        deviceUid: String,
        companyCode: String,
        projectCode: String,
        model: String,
        osVersion: String,
        currentVersion: String?,
        currentBuildNo: Long?
    ): DeviceCheckinResult? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            val url = URL("http://$host:$port/api/v1/devices/checkin")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val body = JSONObject().apply {
                put("deviceUid", deviceUid)
                put("companyCode", companyCode)
                put("projectCode", projectCode)
                put("model", model)
                put("osVersion", osVersion)
                currentVersion?.let { put("currentVersion", it) }
                currentBuildNo?.let { put("currentBuildNo", it) }
            }

            val outputStream: OutputStream = connection.outputStream
            outputStream.write(body.toString().toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val buffer = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    buffer.append(line).append("\n")
                }

                val json = JSONObject(buffer.toString())
                val releaseJson = json.optJSONObject("latestRelease")
                val latestRelease = releaseJson?.let {
                    LatestRelease(
                        version = it.optString("version", ""),
                        buildNo = if (it.has("buildNo") && !it.isNull("buildNo")) it.optLong("buildNo") else null,
                        releaseNote = it.optString("releaseNote", ""),
                        sourceUrl = it.optString("sourceUrl", "")
                    )
                }
                DeviceCheckinResult(latestRelease = latestRelease)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try { reader?.close() } catch (e: Exception) {}
            try { connection?.disconnect() } catch (e: Exception) {}
        }
    }
}
