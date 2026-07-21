package hdisoft.app.appupdate.data.datasource

import android.util.Log
import hdisoft.app.appupdate.domain.model.DownloadState
import hdisoft.app.appupdate.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class UpdateRemoteDataSource {

    companion object {
        private const val TAG = "UpdateRemoteDataSource"
    }

    suspend fun checkUpdate(jsonUrl: String): UpdateInfo? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var reader: BufferedReader? = null
        try {
            val url = URL(jsonUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val stream = connection.inputStream
                reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                val buffer = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    buffer.append(line).append("\n")
                }
                
                val jsonObject = JSONObject(buffer.toString())
                val remoteBuildNo = jsonObject.optLong("buildNo", 0)
                val remoteVersion = jsonObject.optString("version", "1.0.0")
                val buildNote = jsonObject.optString("buildNote", "")
                val downloadUrl = jsonObject.optString("url", "")
                val sha256 = jsonObject.optString("sha256", "")
                val sizeBytes = jsonObject.optLong("sizeBytes", 0)
                val packageName = jsonObject.optString("packageName", "")
                if (remoteBuildNo <= 0 || downloadUrl.isBlank()) {
                    return@withContext null
                }
                UpdateInfo(
                    buildNo = remoteBuildNo,
                    version = remoteVersion,
                    buildNote = buildNote,
                    url = downloadUrl,
                    sha256 = sha256,
                    sizeBytes = sizeBytes,
                    packageName = packageName
                )
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

    fun downloadApk(urlString: String, apkFile: File): Flow<DownloadState> = callbackFlow {
        trySend(DownloadState.Idle)
        val partialFile = File(apkFile.parentFile, "${apkFile.name}.part")
        val connectionRef = AtomicReference<HttpURLConnection?>()
        val inputRef = AtomicReference<InputStream?>()
        val outputRef = AtomicReference<FileOutputStream?>()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)

        val worker = thread(start = true, name = "apk-download") {
            try {
                apkFile.delete()
                partialFile.delete()
                val connection = URL(urlString).openConnection() as HttpURLConnection
                connectionRef.set(connection)
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    trySend(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                    return@thread
                }

                val fileLength = connection.contentLength
                val inputStream = connection.inputStream
                inputRef.set(inputStream)
                val outputStream = FileOutputStream(partialFile)
                outputRef.set(outputStream)
                val data = ByteArray(16 * 1024)
                var total = 0L
                var count = 0

                while (!Thread.currentThread().isInterrupted &&
                    inputStream.read(data).also { count = it } != -1
                ) {
                    total += count
                    outputStream.write(data, 0, count)
                    val downloadedMb = total.toDouble() / (1024 * 1024)
                    val totalMb = fileLength.toDouble() / (1024 * 1024)
                    val progress = if (fileLength > 0) (total * 100 / fileLength).toInt() else 0
                    // Conflated below: a fast local-network transfer emits far more
                    // progress ticks than the UI thread can drain, so trySend must
                    // never be treated as fatal here — only a closed channel (the
                    // collector went away) should abort the worker.
                    if (trySend(DownloadState.Downloading(progress, downloadedMb, totalMb)).isClosed) {
                        return@thread
                    }
                }
                if (Thread.currentThread().isInterrupted) return@thread

                outputStream.flush()
                outputStream.close()
                outputRef.set(null)
                if (fileLength > 0 && total != fileLength.toLong()) {
                    Log.w(TAG, "Incomplete download: received $total of $fileLength bytes")
                    trySend(DownloadState.Error("Incomplete download: received $total of $fileLength bytes"))
                    return@thread
                }
                if (!partialFile.renameTo(apkFile)) {
                    Log.w(TAG, "Could not rename ${partialFile.name} to ${apkFile.name}")
                    trySend(DownloadState.Error("Could not finalize downloaded APK"))
                    return@thread
                }
                completed.set(true)
                trySend(DownloadState.Success(apkFile))
            } catch (e: Exception) {
                Log.e(TAG, "APK download failed", e)
                if (!isClosedForSend) {
                    trySend(DownloadState.Error(e.message ?: "Unknown download error"))
                }
            } finally {
                try { outputRef.getAndSet(null)?.close() } catch (_: Exception) {}
                try { inputRef.getAndSet(null)?.close() } catch (_: Exception) {}
                try { connectionRef.getAndSet(null)?.disconnect() } catch (_: Exception) {}
                if (!completed.get()) partialFile.delete()
                close()
            }
        }

        awaitClose {
            worker.interrupt()
            try { outputRef.getAndSet(null)?.close() } catch (_: Exception) {}
            try { inputRef.getAndSet(null)?.close() } catch (_: Exception) {}
            try { connectionRef.getAndSet(null)?.disconnect() } catch (_: Exception) {}
            if (!completed.get()) partialFile.delete()
        }
        // Progress ticks can arrive far faster than the UI can render them;
        // conflate so the producer never blocks/fails on a slow collector and
        // the UI just always sees the freshest percentage.
    }.conflate().flowOn(Dispatchers.IO)
}
