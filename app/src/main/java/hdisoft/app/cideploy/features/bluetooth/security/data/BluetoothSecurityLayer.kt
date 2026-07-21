package hdisoft.app.cideploy.features.bluetooth.security.data

import android.bluetooth.BluetoothDevice
import android.os.Handler
import android.os.Looper
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnectionState
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnector
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothMode
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Optional application-level authentication layer for BluetoothConnector.
 *
 * Bluetooth pairing/RFCOMM only proves that a Bluetooth device is reachable.
 * This layer proves that both peers know the CI-Deploy protocol and secret
 * before forwarding application messages to the chat screen.
 */
class BluetoothSecurityLayer(
    private val connector: BluetoothConnector,
    private val localRole: BluetoothMode,
    private val securityEnabled: Boolean = true,
    private val simpleAuthentication: Boolean = true,
    private val keepHostListening: Boolean = false,
    private val timeoutMillis: Long = 10_000L
) {
    companion object {
        private const val PROTOCOL = "CDS1"
        private const val APP_ID = "CI_DEPLOY"
        // This is an app-identity secret, not a replacement for transport encryption.
        private const val SHARED_SECRET = "CI_DEPLOY_BLUETOOTH_SHARED_SECRET_V1"
        private const val HELLO = "$PROTOCOL|HELLO"
        private const val AUTH = "$PROTOCOL|AUTH"
        private const val AUTH_OK = "$PROTOCOL|AUTH_OK"
        private const val REJECT = "$PROTOCOL|REJECT"

        @Volatile
        var sharedSecurityLayer: BluetoothSecurityLayer? = null
    }

    interface Listener {
        fun onStateChanged(state: BluetoothConnectionState)
        fun onDeviceFound(device: BluetoothDevice)
        fun onDiscoveryFinished()
        fun onAuthenticated(remoteDevice: BluetoothDevice?)
        fun onAuthenticationRejected(reason: String)
        fun onMessageReceived(message: String)
        fun onMessageSent(message: String)
        fun onError(message: String)
    }

    var listener: Listener? = null
    private val observers = mutableSetOf<Listener>()
    fun addListener(observer: Listener) { observers.add(observer) }
    fun removeListener(observer: Listener) { observers.remove(observer) }
    private inline fun notifyListeners(block: (Listener) -> Unit) {
        listener?.let(block)
        observers.toList().forEach(block)
    }

    @Volatile
    var isAuthenticated: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val random = SecureRandom()
    private var localNonce = ""
    private var remoteNonce = ""
    private var authVerified = false
    private var helloReceived = false
    private var simpleAuthSent = false
    private var simpleAuthReceived = false
    private var authTimeout: Runnable? = null

    private val connectorCallback = object : BluetoothConnector.Callback {
        override fun onStateChanged(state: BluetoothConnectionState) {
            notifyListeners { it.onStateChanged(state) }
            if (!securityEnabled && state == BluetoothConnectionState.CONNECTED) {
                isAuthenticated = true
                notifyListeners { it.onAuthenticated(connector.connectedDevice) }
                return
            }
            when (state) {
                BluetoothConnectionState.CONNECTED -> beginHandshake()
                BluetoothConnectionState.DISCONNECTED,
                BluetoothConnectionState.IDLE -> {
                    resetHandshake()
                    if (keepHostListening && localRole == BluetoothMode.HOST && state == BluetoothConnectionState.DISCONNECTED) {
                        // Give ChatActivity time to finish and BluetoothTestActivity
                        // time to reattach its listener before accepting again.
                        handler.postDelayed({ connector.startListening() }, 1_500L)
                    }
                }
                else -> Unit
            }
        }

        override fun onDeviceFound(device: BluetoothDevice) {
            notifyListeners { it.onDeviceFound(device) }
        }

        override fun onDiscoveryFinished() {
            notifyListeners { it.onDiscoveryFinished() }
        }

        override fun onMessageReceived(message: String) {
            if (simpleAuthentication && message == APP_ID) {
                simpleAuthReceived = true
                if (!simpleAuthSent) {
                    simpleAuthSent = true
                    connector.sendMessage(APP_ID)
                }
                if (simpleAuthSent && simpleAuthReceived) authenticate()
                return
            }
            if (!securityEnabled) {
                notifyListeners { it.onMessageReceived(message) }
                return
            }
            if (message.startsWith("$PROTOCOL|")) {
                handleSecurityMessage(message)
            } else if (isAuthenticated) {
                notifyListeners { it.onMessageReceived(message) }
            }
            // All non-protocol messages are intentionally dropped before auth.
        }

        override fun onMessageSent(message: String) {
            if (!message.startsWith("$PROTOCOL|")) {
                notifyListeners { it.onMessageSent(message) }
            }
        }

        override fun onError(message: String) {
            notifyListeners { it.onError(message) }
        }
    }

    init {
        require(localRole == BluetoothMode.HOST || localRole == BluetoothMode.CLIENT) {
            "BluetoothSecurityLayer requires HOST or CLIENT role"
        }
        connector.callback = connectorCallback
        sharedSecurityLayer = this
    }

    fun startDiscovery() = connector.startDiscovery()

    fun stopDiscovery() = connector.stopDiscovery()

    fun getPairedDevices(): List<BluetoothDevice> = connector.getPairedDevices()

    fun connect(device: BluetoothDevice) = connector.connect(device)

    fun startListening() = connector.startListening()

    fun sendMessage(message: String): Boolean {
        if ((!isAuthenticated && securityEnabled) || message.isBlank() || message.contains('\n')) return false
        return connector.sendMessage(message)
    }

    fun disconnect() = connector.disconnect()

    fun close() {
        authTimeout?.let(handler::removeCallbacks)
        if (connector.callback === connectorCallback) connector.callback = null
        if (sharedSecurityLayer === this) sharedSecurityLayer = null
    }

    private fun beginHandshake() {
        if (!securityEnabled) return
        if (simpleAuthentication) {
            simpleAuthSent = true
            connector.sendMessage(APP_ID)
            authTimeout = Runnable {
                if (!isAuthenticated) reject("Simple authentication timeout")
            }
            handler.postDelayed(authTimeout!!, timeoutMillis)
            return
        }
        resetHandshake(clearStateOnly = true)
        localNonce = randomNonce()
        connector.sendMessage("$HELLO|app=$APP_ID|role=${localRole.name}|nonce=$localNonce")
        val timeout = Runnable {
            if (!isAuthenticated) reject("Authentication timeout")
        }
        authTimeout = timeout
        handler.postDelayed(timeout, timeoutMillis)
    }

    @Synchronized
    private fun resetHandshake(clearStateOnly: Boolean = false) {
        authTimeout?.let(handler::removeCallbacks)
        authTimeout = null
        isAuthenticated = false
        authVerified = false
        helloReceived = false
        simpleAuthSent = false
        simpleAuthReceived = false
        remoteNonce = ""
        if (!clearStateOnly) localNonce = ""
    }

    private fun handleSecurityMessage(message: String) {
        val fields = message.split('|')
        if (fields.size < 2 || fields[0] != PROTOCOL) {
            reject("Unsupported security protocol")
            return
        }
        when (fields[1]) {
            "HELLO" -> handleHello(fields)
            "AUTH" -> handleAuth(fields)
            "AUTH_OK" -> if (authVerified) authenticate()
            else -> reject("Unknown security message")
        }
    }

    private fun handleHello(fields: List<String>) {
        val values = fields.drop(2).mapNotNull {
            val pair = it.split('=', limit = 2)
            if (pair.size == 2) pair[0] to pair[1] else null
        }.toMap()
        val app = values["app"]
        val role = values["role"]
        val nonce = values["nonce"]
        val expectedRemoteRole = if (localRole == BluetoothMode.HOST) {
            BluetoothMode.CLIENT.name
        } else {
            BluetoothMode.HOST.name
        }
        if (app != APP_ID || role != expectedRemoteRole || nonce.isNullOrBlank()) {
            reject("Peer is not a compatible CI_DEPLOY app")
            return
        }
        remoteNonce = nonce
        helloReceived = true
        val proof = proof(remoteNonce, localNonce)
        connector.sendMessage("$AUTH|nonce=$localNonce|proof=$proof")
    }

    private fun handleAuth(fields: List<String>) {
        if (!helloReceived) {
            reject("Authentication arrived before HELLO")
            return
        }
        val values = fields.drop(2).mapNotNull {
            val pair = it.split('=', limit = 2)
            if (pair.size == 2) pair[0] to pair[1] else null
        }.toMap()
        val nonce = values["nonce"]
        val receivedProof = values["proof"]
        val expectedProof = proof(remoteNonce, localNonce)
        // Accept the two historical nonce orders for one migration cycle so a
        // device that has not yet received the latest APK can still authenticate.
        val legacyProofA = rawProof(localNonce, remoteNonce)
        val legacyProofB = rawProof(remoteNonce, localNonce)
        if (nonce != remoteNonce || receivedProof.isNullOrBlank() ||
            receivedProof != expectedProof && receivedProof != legacyProofA && receivedProof != legacyProofB
        ) {
            reject("Peer authentication proof is invalid (nonce=${nonce?.take(8)}, expected=${remoteNonce.take(8)}, received=${receivedProof?.take(8)}, calculated=${expectedProof.take(8)})")
            return
        }
        authVerified = true
        connector.sendMessage(AUTH_OK)
        authenticate()
    }

    private fun authenticate() {
        if (isAuthenticated) return
        isAuthenticated = true
        authTimeout?.let(handler::removeCallbacks)
        authTimeout = null
        notifyListeners { it.onAuthenticated(connector.connectedDevice) }
    }

    private fun reject(reason: String) {
        if (isAuthenticated) return
        notifyListeners { it.onAuthenticationRejected(reason) }
        connector.sendMessage("$REJECT|reason=${reason.replace('|', '_')}")
        connector.disconnect()
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun proof(firstNonce: String, secondNonce: String): String {
        // Both peers must derive the same input. Sorting removes the sender/receiver
        // perspective difference (peer A sees A/B, peer B sees B/A).
        val ordered = listOf(firstNonce, secondNonce).sorted()
        return rawProof(ordered[0], ordered[1])
    }

    private fun rawProof(firstNonce: String, secondNonce: String): String {
        val value = "$SHARED_SECRET|$firstNonce|$secondNonce"
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
