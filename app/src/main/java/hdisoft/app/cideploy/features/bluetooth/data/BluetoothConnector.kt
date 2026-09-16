package hdisoft.app.cideploy.features.bluetooth.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Build
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class BluetoothConnectionState {
    IDLE,
    LISTENING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

enum class BluetoothMode {
    NONE,
    HOST,
    CLIENT,
    P2P
}

@SuppressLint("MissingPermission")
class BluetoothConnector(
    context: Context,
    var callback: Callback? = null
) {
    companion object {
        private const val TAG = "BluetoothConnector"
        private const val APP_NAME = "CIDeployBluetooth"
        // Standard SPP UUID
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        @Volatile
        var sharedConnector: BluetoothConnector? = null
    }

    interface Callback {
        fun onStateChanged(state: BluetoothConnectionState)
        fun onDeviceFound(device: BluetoothDevice)
        fun onMessageReceived(message: String)
        fun onMessageSent(message: String)
        fun onError(message: String)
        fun onDiscoveryFinished()
    }

    private val context = context.applicationContext
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private var discoveryReceiverRegistered = false

    var currentMode = BluetoothMode.NONE
        private set

    var connectedDevice: BluetoothDevice? = null
        private set
    
    var currentState = BluetoothConnectionState.IDLE
        private set(value) {
            if (field != value) {
                field = value
                callback?.onStateChanged(value)
            }
        }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let { callback?.onDeviceFound(it) }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                callback?.onDiscoveryFinished()
            }
        }
    }

    fun isSupported(): Boolean = bluetoothAdapter != null

    fun isEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasConnect = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
            // CI-Deploy Host is passive and does not request discoverable mode,
            // so BLUETOOTH_ADVERTISE is not required for connect/scan.
            hasScan && hasConnect
        } else {
            val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.isLocationEnabled == true
            } else {
                // isLocationEnabled was introduced in API 28. On Android 8
                // permission is the only safe check; discovery will report a
                // platform error if the provider is unavailable.
                true
            }
            fine && locationEnabled
        }
    }

    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasPermissions() || bluetoothAdapter == null) return emptyList()
        return try {
            bluetoothAdapter.bondedDevices.toList()
        } catch (e: SecurityException) {
            callback?.onError("Missing permission: BLUETOOTH_CONNECT")
            emptyList()
        }
    }

    fun startDiscovery() {
        if (!hasPermissions() || bluetoothAdapter == null) {
            callback?.onError("Missing permissions for discovery")
            return
        }
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            if (!discoveryReceiverRegistered) {
                context.registerReceiver(discoveryReceiver, IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_FOUND)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                })
                discoveryReceiverRegistered = true
            }
            bluetoothAdapter.startDiscovery()
            Log.d(TAG, "Bluetooth discovery started")
        } catch (e: SecurityException) {
            callback?.onError("SecurityException: " + e.message)
        } catch (e: Exception) {
            callback?.onError("Failed to start discovery: " + e.message)
        }
    }

    fun stopDiscovery() {
        if (bluetoothAdapter == null) return
        try {
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            if (discoveryReceiverRegistered) {
                context.unregisterReceiver(discoveryReceiver)
                discoveryReceiverRegistered = false
            }
        } catch (e: Exception) {
            // Might not be registered
        }
    }

    @Synchronized
    fun startListening() {
        Log.d(TAG, "startListening")
        if (!isEnabled()) {
            callback?.onError("Bluetooth is not enabled")
            return
        }
        if (!hasPermissions()) {
            callback?.onError("Missing Bluetooth permissions")
            return
        }

        stopDiscovery()
        currentMode = BluetoothMode.HOST
        
        // Cancel any threads currently trying to connect
        connectThread?.cancel()
        connectThread = null

        // Cancel any running connection
        connectedThread?.cancel()
        connectedThread = null

        // Start AcceptThread to listen for connections
        if (acceptThread == null) {
            acceptThread = AcceptThread()
            acceptThread?.start()
        }
        currentState = BluetoothConnectionState.LISTENING
    }

    @Synchronized
    fun startClientMode() {
        Log.d(TAG, "startClientMode")
        acceptThread?.cancel()
        acceptThread = null
        connectThread?.cancel()
        connectThread = null
        connectedThread?.cancel()
        connectedThread = null
        connectedDevice = null
        currentMode = BluetoothMode.CLIENT
        currentState = BluetoothConnectionState.IDLE
    }

    @Synchronized
    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "connect to: $device")
        if (!isEnabled()) {
            callback?.onError("Bluetooth is not enabled")
            return
        }
        if (!hasPermissions()) {
            callback?.onError("Missing Bluetooth permissions")
            return
        }
        currentMode = BluetoothMode.CLIENT

        // Cancel discovery if running
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {}

        // Cancel any threads currently trying to connect or run server
        if (currentState == BluetoothConnectionState.CONNECTING) {
            connectThread?.cancel()
            connectThread = null
        }

        connectedThread?.cancel()
        connectedThread = null
        acceptThread?.cancel()
        acceptThread = null
        connectedDevice = null

        // Start ConnectThread
        connectThread = ConnectThread(device)
        connectThread?.start()
        currentState = BluetoothConnectionState.CONNECTING
    }

    @Synchronized
    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        Log.d(TAG, "manageConnectedSocket")

        // Cancel the threads that completed the connection
        connectThread = null
        acceptThread?.cancel()
        acceptThread = null

        // Start thread to manage connection and perform transmissions
        connectedDevice = device
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
        currentState = BluetoothConnectionState.CONNECTED
    }

    @Synchronized
    fun disconnect() {
        Log.d(TAG, "disconnect")
        connectThread?.cancel()
        connectThread = null
        
        connectedThread?.cancel()
        connectedThread = null
        
        acceptThread?.cancel()
        acceptThread = null

        stopDiscovery()
        connectedDevice = null
        currentMode = BluetoothMode.NONE
        currentState = BluetoothConnectionState.DISCONNECTED
        currentState = BluetoothConnectionState.IDLE
    }

    fun sendMessage(message: String): Boolean {
        val targetThread = connectedThread ?: return false
        val bytes = (message + "\n").toByteArray()
        return try {
            targetThread.write(bytes)
            callback?.onMessageSent(message)
            true
        } catch (e: Exception) {
            false
        }
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } catch (e: IOException) {
                Log.e(TAG, "Socket's listen() method failed", e)
                callback?.onError("Server listen failed: ${e.message}")
                null
            }
        }

        override fun run() {
            val listener = serverSocket
            if (listener == null) {
                synchronized(this@BluetoothConnector) {
                    if (acceptThread === this) {
                        acceptThread = null
                        currentState = BluetoothConnectionState.IDLE
                    }
                }
                return
            }

            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    listener.accept()
                } catch (e: IOException) {
                    if (acceptThread === this) {
                        Log.e(TAG, "Socket's accept() method failed", e)
                        callback?.onError("Host listener stopped: ${e.message}")
                    }
                    shouldLoop = false
                    null
                }
                socket?.let {
                    synchronized(this@BluetoothConnector) {
                        when (currentState) {
                            BluetoothConnectionState.LISTENING, BluetoothConnectionState.CONNECTING -> {
                                // Situation normal. Start the connected thread.
                                manageConnectedSocket(it, it.remoteDevice)
                            }
                            BluetoothConnectionState.IDLE, BluetoothConnectionState.CONNECTED -> {
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    it.close()
                                } catch (e: IOException) {
                                    Log.e(TAG, "Could not close unwanted socket", e)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            synchronized(this@BluetoothConnector) {
                if (acceptThread === this) {
                    acceptThread = null
                    currentState = BluetoothConnectionState.IDLE
                }
            }
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            try {
                device.createRfcommSocketToServiceRecord(MY_UUID)
            } catch (e: IOException) {
                Log.e(TAG, "Socket's create() method failed", e)
                callback?.onError("Socket creation failed: ${e.message}")
                null
            }
        }

        override fun run() {
            val clientSocket = socket
            if (clientSocket == null) {
                synchronized(this@BluetoothConnector) {
                    if (connectThread === this) {
                        connectThread = null
                        currentState = BluetoothConnectionState.IDLE
                    }
                }
                return
            }

            try {
                clientSocket.connect()
            } catch (e: IOException) {
                if (connectThread === this) {
                    Log.e(TAG, "Connection failed", e)
                    callback?.onError("Connection failed: ${e.message}")
                }
                try {
                    clientSocket.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "Could not close the client socket", e2)
                }
                synchronized(this@BluetoothConnector) {
                    if (connectThread === this) {
                        connectThread = null
                        currentState = BluetoothConnectionState.IDLE
                    }
                }
                return
            }

            synchronized(this@BluetoothConnector) {
                if (connectThread === this) {
                    manageConnectedSocket(clientSocket, device)
                } else {
                    try {
                        clientSocket.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Could not close stale client socket", e)
                    }
                }
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream

        override fun run() {
            val reader = inputStream.bufferedReader()
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    callback?.onMessageReceived(line)
                }
            } catch (e: IOException) {
                Log.d(TAG, "Input stream was disconnected", e)
            } finally {
                synchronized(this@BluetoothConnector) {
                    if (connectedThread === this) {
                        connectedThread = null
                        connectedDevice = null
                        currentState = BluetoothConnectionState.DISCONNECTED
                    }
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                outputStream.write(bytes)
                outputStream.flush()
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
                callback?.onError("Failed to send message: ${e.message}")
                throw e
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }
}
