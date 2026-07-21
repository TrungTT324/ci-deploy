package hdisoft.app.logcat.domain.repository

import hdisoft.app.logcat.domain.model.LogStreamConfig

interface ExternalLogStreamer {
    fun start(mode: LogStreamConfig.Mode, protocol: LogStreamConfig.Protocol, ip: String, port: Int, localIp: String)
    fun stop()
    fun sendLog(log: String)
    fun getStatus(): String
    var onLogReceivedListener: ((String) -> Unit)?
    var onStatusChangedListener: ((String) -> Unit)?
}
