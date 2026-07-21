package hdisoft.app.logcat.domain.repository

import hdisoft.app.logcat.domain.model.LogStreamConfig
import java.io.File

interface LogcatRepository {
    fun getStreamConfig(): LogStreamConfig
    fun saveStreamConfig(config: LogStreamConfig)
    fun startStream(onLogReceived: (String) -> Unit, onStatusChanged: (String) -> Unit)
    fun stopStream()
    fun isStreamingActive(): Boolean
    fun getStreamStatusText(): String
    suspend fun clearLogs(wasActive: Boolean): Boolean
    suspend fun exportLogs(logs: List<String>, cacheDir: File): File?
}
