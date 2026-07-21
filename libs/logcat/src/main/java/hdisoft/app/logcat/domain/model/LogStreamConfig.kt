package hdisoft.app.logcat.domain.model

data class LogStreamConfig(
    val mode: Mode,
    val ip: String,
    val port: Int,
    val protocol: Protocol = Protocol.WEBSOCKET
) {
    enum class Mode {
        DIRECT, SERVER, CLIENT
    }
    enum class Protocol {
        WEBSOCKET, TCP, UDP
    }
}
