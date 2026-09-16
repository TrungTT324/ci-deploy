package hdisoft.app.cideploy.features.bluetooth.presentation

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hdisoft.app.cideploy.R
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnector
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnectionState
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothMode
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothPeerStore
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothRecentStore
import hdisoft.app.cideploy.features.bluetooth.security.data.BluetoothSecurityLayer

@SuppressLint("MissingPermission")
class BluetoothTestActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 201
        private const val REQUEST_ENABLE_BT = 202
    }

    private lateinit var tvStatus: TextView
    private lateinit var viewStatusDot: View
    private lateinit var btnStopHost: Button
    private lateinit var btnScanDevices: Button
    private lateinit var tvModeDescription: TextView
    private lateinit var cardClientDevices: View
    private lateinit var tvEventLog: TextView
    private lateinit var scrollEventLog: android.widget.ScrollView
    private lateinit var mainScroll: android.widget.ScrollView

    private lateinit var rvPairedDevices: RecyclerView
    private lateinit var rvRecentDevices: RecyclerView
    private lateinit var tvRecentEmpty: TextView
    private lateinit var rvDiscoveredDevices: RecyclerView

    private lateinit var connector: BluetoothConnector
    private var securityLayer: BluetoothSecurityLayer? = null
    private var securityRole: BluetoothMode? = null
    private var securityEnabled = true
    private lateinit var peerStore: BluetoothPeerStore
    private lateinit var recentStore: BluetoothRecentStore
    private var p2pPeers: List<BluetoothDevice> = emptyList()
    private var p2pIndex = 0
    private var p2pActive = false
    private var p2pRetryPending = false
    private var preferredMode: BluetoothMode = BluetoothMode.NONE
    private var modeAutoStarted = false
    private val settingsRequestCode = 4201
    private val modePrefs by lazy { getSharedPreferences("bluetooth_peer", MODE_PRIVATE) }

    private val pairedDevicesList = mutableListOf<BluetoothDevice>()
    private val recentDevicesList = mutableListOf<BluetoothDevice>()
    private val discoveredDevicesList = mutableListOf<BluetoothDevice>()

    private lateinit var pairedAdapter: DeviceAdapter
    private lateinit var recentAdapter: DeviceAdapter
    private lateinit var discoveredAdapter: DeviceAdapter
    private var chatOpening = false

    private val securityListener = object : BluetoothSecurityLayer.Listener {
        override fun onStateChanged(state: BluetoothConnectionState) {
            appendEventLog("STATE -> $state")
            if (state == BluetoothConnectionState.DISCONNECTED && p2pActive && !p2pRetryPending) {
                scheduleNextP2pPeer("transport disconnected")
            }
            runOnUiThread { updateConnectionUi(state) }
        }

        override fun onDeviceFound(device: BluetoothDevice) {
            appendEventLog("DEVICE_FOUND name=${device.name ?: "Unknown"} address=${device.address}")
            runOnUiThread {
                if (!discoveredDevicesList.contains(device)) {
                    discoveredDevicesList.add(device)
                    discoveredAdapter.notifyItemInserted(discoveredDevicesList.size - 1)
                }
            }
        }

        override fun onMessageReceived(message: String) {}

        override fun onMessageSent(message: String) {}

        override fun onAuthenticated(device: BluetoothDevice?) {
            device?.let { peerStore.save(it); recentStore.add(it, connector.currentMode); appendEventLog("PEER_SAVED ${it.address}; RECENT_SAVED ${BluetoothRecentStore.formatTime(System.currentTimeMillis())}") }
            appendEventLog("AUTHENTICATED device=${device?.name ?: "Unknown"}")
            runOnUiThread {
                setStatus("Status: Authenticated", Color.parseColor("#4CAF50"))
                if (!chatOpening) {
                    chatOpening = true
                    startActivity(Intent(this@BluetoothTestActivity, BluetoothChatActivity::class.java))
                }
            }
        }

        override fun onAuthenticationRejected(reason: String) {
            if (p2pActive && !p2pRetryPending) {
                appendEventLog("P2P: skip peer after auth failure")
                scheduleNextP2pPeer("authentication rejected: $reason")
            }
            appendEventLog("AUTH_REJECTED: $reason")
            runOnUiThread {
                setStatus("Status: Authentication rejected", Color.RED)
                Toast.makeText(this@BluetoothTestActivity, reason, Toast.LENGTH_LONG).show()
            }
        }

        override fun onDiscoveryFinished() {
            appendEventLog("DISCOVERY_FINISHED")
            runOnUiThread {
                btnScanDevices.text = "Scan for devices"
                btnScanDevices.isEnabled = true
                Toast.makeText(this@BluetoothTestActivity, "Scan finished", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onError(message: String) {
            appendEventLog("ERROR: $message")
            runOnUiThread {
                Toast.makeText(this@BluetoothTestActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_test)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbarBluetooth)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Bind Views
        tvStatus = findViewById(R.id.tvStatus)
        viewStatusDot = findViewById(R.id.viewStatusDot)
        btnStopHost = findViewById(R.id.btnStopHost)
        peerStore = BluetoothPeerStore(this)
        recentStore = BluetoothRecentStore(this)
        preferredMode = modePrefs.getString("preferred_mode", BluetoothMode.NONE.name)
            ?.let { runCatching { BluetoothMode.valueOf(it) }.getOrDefault(BluetoothMode.NONE) }
            ?: BluetoothMode.NONE
        btnScanDevices = findViewById(R.id.btnScanDevices)
        mainScroll = findViewById(R.id.bluetoothTestScroll)
        tvModeDescription = findViewById(R.id.tvModeDescription)
        cardClientDevices = findViewById(R.id.cardClientDevices)
        tvEventLog = findViewById(R.id.tvBluetoothEventLog)
        scrollEventLog = findViewById(R.id.scrollBluetoothEventLog)

        rvPairedDevices = findViewById(R.id.rvPairedDevices)
        rvRecentDevices = findViewById(R.id.rvRecentDevices)
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty)
        rvDiscoveredDevices = findViewById(R.id.rvDiscoveredDevices)

        // Hide chat layout if it exists (it's unused now)
        findViewById<View?>(R.id.cardChat)?.visibility = View.GONE

        // Initialize Lists and Adapters
        pairedAdapter = DeviceAdapter(pairedDevicesList) { device ->
            if (connector.currentMode == BluetoothMode.HOST) {
                appendEventLog("HOST: paired device selected for inspection only; connect is disabled")
            } else connectToDevice(device)
        }
        recentAdapter = DeviceAdapter(recentDevicesList) { device -> connectToDevice(device) }
        discoveredAdapter = DeviceAdapter(discoveredDevicesList) { device ->
            when (device.bondState) {
                BluetoothDevice.BOND_NONE -> {
                    Toast.makeText(
                        this,
                        "Pairing with ${device.name ?: "Unknown"}; tap again after pairing completes.",
                        Toast.LENGTH_LONG
                    ).show()
                    if (!device.createBond()) {
                        Toast.makeText(this, "Could not start pairing", Toast.LENGTH_LONG).show()
                    }
                }
                BluetoothDevice.BOND_BONDING ->
                    Toast.makeText(this, "Pairing is still in progress...", Toast.LENGTH_SHORT).show()
                else -> if (connector.currentMode == BluetoothMode.HOST) {
                    appendEventLog("HOST: discovered device selected for inspection only; connect is disabled")
                } else connectToDevice(device)
            }
        }

        rvPairedDevices.layoutManager = LinearLayoutManager(this)
        rvPairedDevices.adapter = pairedAdapter
        rvRecentDevices.layoutManager = LinearLayoutManager(this)
        rvRecentDevices.adapter = recentAdapter

        rvDiscoveredDevices.layoutManager = LinearLayoutManager(this)
        rvDiscoveredDevices.adapter = discoveredAdapter

        // Setup Connector
        connector = BluetoothConnector.sharedConnector ?: BluetoothConnector(this).also {
            BluetoothConnector.sharedConnector = it
        }
        securityLayer = BluetoothSecurityLayer.sharedSecurityLayer
        if (securityLayer != null) {
            securityRole = connector.currentMode
            securityLayer?.listener = securityListener
        } else {
            connector.callback = null
        }

        // Click Listeners
        btnStopHost.setOnClickListener { stopHostService() }
        btnScanDevices.setOnClickListener { appendEventLog("ACTION: scan devices"); if (connector.currentMode == BluetoothMode.HOST) scanAsHost() else startScan() }

        // Load paired devices if enabled on startup
        if (connector.isSupported() && connector.isEnabled()) {
            if (hasRequiredPermissions()) {
                loadPairedDevices()
            } else {
                requestPermissions()
            }
        } else if (connector.isSupported()) {
            requestPermissions()
        } else {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
        }

        updateBluetoothStatus()
        appendEventLog("SCREEN_READY mode=${connector.currentMode} state=${connector.currentState}")
        maybeAutoStartPreferredMode()
    }

    override fun onResume() {
        super.onResume()
        securityEnabled = modePrefs.getBoolean("security_enabled", securityEnabled)
        // Re-assign this activity's callback to handle state updates when returning from chat screen
        securityLayer?.listener = securityListener
        chatOpening = false
        updateModeUi(connector.currentMode)
        updateConnectionUi(connector.currentState)
        if (connector.isEnabled() && hasRequiredPermissions()) loadPairedDevices()
        maybeAutoStartPreferredMode()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean { menu.add(0, 9001, 0, "Enable Bluetooth").setIcon(android.R.drawable.stat_sys_data_bluetooth).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS); menu.add(0, 9002, 1, "Settings").setIcon(android.R.drawable.ic_menu_preferences).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS); return true }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { if (item.itemId == 9001) { enableBluetooth(); return true }; if (item.itemId == 9002) { startActivityForResult(Intent(this, BluetoothSettingsActivity::class.java), settingsRequestCode); return true }; return super.onOptionsItemSelected(item) }

    private fun restartFromSavedSettings() {
        appendEventLog("SETTINGS: saved; restarting Bluetooth mode")
        stopService(Intent(this, BluetoothHostService::class.java))
        securityLayer?.close()
        securityLayer = null
        securityRole = null
        if (connector.currentState != BluetoothConnectionState.IDLE) connector.disconnect()
        preferredMode = modePrefs.getString("preferred_mode", BluetoothMode.NONE.name)?.let { runCatching { BluetoothMode.valueOf(it) }.getOrDefault(BluetoothMode.NONE) } ?: BluetoothMode.NONE
        securityEnabled = modePrefs.getBoolean("security_enabled", true)
        modeAutoStarted = false
        updateModeUi(preferredMode)
        maybeAutoStartPreferredMode()
    }

    private fun hasRequiredPermissions(): Boolean {
        return connector.hasPermissions()
    }

    private fun requestPermissions() {
        val permissionsList = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsList.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissionsList.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsList.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsList.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        val neededPermissions = permissionsList.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                neededPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val locationOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
                    (getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager)?.isLocationEnabled == true
                if (!locationOn) {
                    appendEventLog("PERMISSION: Android 11 requires Location Services enabled for Bluetooth scan")
                    startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    return
                }
            }
            enableBluetooth()
            loadPairedDevices()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPairedDevices()
                enableBluetooth()
            } else {
                Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableBluetooth() {
        appendEventLog("ACTION: enable Bluetooth")
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        } else {
            loadPairedDevices()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == settingsRequestCode && resultCode == RESULT_OK) restartFromSavedSettings()
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                loadPairedDevices()
            }
            updateBluetoothStatus()
        }
    }

    private fun startHost() {
        savePreferredMode(BluetoothMode.HOST)
        appendEventLog("HOST: validating permissions and adapter")
        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }
        if (!connector.isEnabled()) {
            enableBluetooth()
            return
        }

        ensureSecurityLayer(BluetoothMode.HOST, keepHostListening = true)

        // Host is a passive RFCOMM server. It does not start Bluetooth device
        // discovery or request discoverability; the Client must already know
        // or pair with this device before connecting.
        appendEventLog("HOST: passive listen only; discovery disabled")
        securityLayer?.startListening()
        ContextCompat.startForegroundService(this, Intent(this, BluetoothHostService::class.java))
        appendEventLog("HOST: listening started")
        updateModeUi(BluetoothMode.HOST)
        Toast.makeText(this, "Host mode started. Waiting for a client...", Toast.LENGTH_SHORT).show()
    }

    private fun stopHostService() {
        appendEventLog("ACTION: stop Host service")
        stopService(Intent(this, BluetoothHostService::class.java))
        securityLayer?.disconnect()
        btnStopHost.isEnabled = false
    }

    private fun startClientMode() {
        savePreferredMode(BluetoothMode.CLIENT)
        appendEventLog("CLIENT: mode selected")
        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }
        if (!connector.isEnabled()) {
            enableBluetooth()
            return
        }

        connector.startClientMode()
        ensureSecurityLayer(BluetoothMode.CLIENT)
        updateModeUi(BluetoothMode.CLIENT)
        setStatus("Status: Client Ready", Color.parseColor("#29B6F6"))
        appendEventLog("CLIENT: auto discovery on mode start")
        startScan()
    }

    private fun startP2pMode() {
        savePreferredMode(BluetoothMode.P2P)
        if (!hasRequiredPermissions() || !connector.isEnabled()) { requestPermissions(); if (!connector.isEnabled()) enableBluetooth(); return }
        val paired = connector.getPairedDevices()
        val savedAddress = peerStore.address()
        val peers = paired.sortedWith(compareByDescending<BluetoothDevice> { it.address == savedAddress }.thenBy { it.address })
        if (peers.isEmpty()) {
            appendEventLog("P2P: no paired devices found")
            Toast.makeText(this, "Pair at least one Bluetooth device first", Toast.LENGTH_LONG).show()
            return
        }
        p2pPeers = peers
        p2pIndex = 0
        p2pActive = true
        connectNextP2pPeer()
    }

    private fun connectNextP2pPeer() {
        if (!p2pActive || p2pIndex >= p2pPeers.size) {
            p2pActive = false
            appendEventLog("P2P: no more paired peers to try")
            return
        }
        val peer = p2pPeers[p2pIndex++]
        p2pRetryPending = false
        appendEventLog("P2P: TRY ${p2pIndex}/${p2pPeers.size} name=${peer.name ?: "Unknown"} address=${peer.address}")
        // Deterministic role prevents both devices from acting as Client.
        val localKey = BluetoothAdapter.getDefaultAdapter()?.name.orEmpty()
        val peerKey = peer.name.orEmpty()
        if (localKey.isNotBlank() && localKey < peerKey) {
            appendEventLog("P2P: peer role decision HOST (local=$localKey peer=$peerKey)")
            ensureSecurityLayer(BluetoothMode.HOST)
            connector.startListening()
            updateModeUi(BluetoothMode.HOST)
        } else {
            appendEventLog("P2P: peer role decision CLIENT (local=$localKey peer=$peerKey)")
            connector.startClientMode()
            ensureSecurityLayer(BluetoothMode.CLIENT)
            updateModeUi(BluetoothMode.CLIENT)
            securityLayer?.connect(peer)
        }
        appendEventLog("P2P: waiting/connecting paired peer=${peer.name ?: peer.address}")
    }

    private fun savePreferredMode(mode: BluetoothMode) {
        preferredMode = mode
        modePrefs.edit().putString("preferred_mode", mode.name).apply()
        appendEventLog("PERSIST: preferred mode=${mode.name}")
    }

    private fun maybeAutoStartPreferredMode() {
        if (modeAutoStarted || preferredMode == BluetoothMode.NONE || !hasRequiredPermissions() || !connector.isEnabled()) return
        modeAutoStarted = true
        appendEventLog("AUTO_START: mode=${preferredMode.name}")
        when (preferredMode) {
            BluetoothMode.HOST -> startHost()
            BluetoothMode.CLIENT -> startClientMode()
            BluetoothMode.P2P -> startP2pMode()
            BluetoothMode.NONE -> Unit
        }
    }

    private fun scheduleNextP2pPeer(reason: String) {
        if (!p2pActive || p2pRetryPending) return
        p2pRetryPending = true
        appendEventLog("P2P: SKIP ${p2pIndex}/${p2pPeers.size} reason=$reason")
        runOnUiThread {
            if (connector.currentState != BluetoothConnectionState.IDLE) connector.disconnect()
            window.decorView.postDelayed({ connectNextP2pPeer() }, 300L)
        }
    }

    private fun startScan() {
        val scrollY = mainScroll.scrollY
        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }
        if (!connector.isEnabled()) {
            enableBluetooth()
            return
        }
        if (connector.currentMode != BluetoothMode.CLIENT) {
            connector.startClientMode()
            updateModeUi(BluetoothMode.CLIENT)
        }
        ensureSecurityLayer(BluetoothMode.CLIENT)

        discoveredDevicesList.clear()
        discoveredAdapter.notifyDataSetChanged()
        
        btnScanDevices.text = "Scanning..."
        btnScanDevices.isEnabled = false
        
        securityLayer?.startDiscovery()
        mainScroll.post { mainScroll.scrollTo(0, scrollY) }
        appendEventLog("CLIENT: discovery started")
    }

    private fun scanAsHost() {
        if (!hasRequiredPermissions() || !connector.isEnabled()) {
            appendEventLog("HOST_SCAN: Bluetooth/permission unavailable")
            requestPermissions()
            return
        }
        ensureSecurityLayer(BluetoothMode.HOST)
        discoveredDevicesList.clear()
        discoveredAdapter.notifyDataSetChanged()
        cardClientDevices.visibility = View.VISIBLE
        securityLayer?.startDiscovery()
        appendEventLog("HOST_SCAN: discovery started; Host remains listen-only")
    }

    private fun loadPairedDevices() {
        if (!hasRequiredPermissions()) return
        pairedDevicesList.clear()
        val paired = connector.getPairedDevices()
        val recentOrder = recentStore.recent().mapIndexed { index, item -> item.address to index }.toMap()
        val recentAddresses = recentOrder.keys
        recentDevicesList.clear()
        recentDevicesList.addAll(paired.filter { it.address in recentAddresses }.sortedBy { recentOrder[it.address] })
        recentAdapter.notifyDataSetChanged()
        tvRecentEmpty.visibility = if (recentDevicesList.isEmpty()) View.VISIBLE else View.GONE
        pairedDevicesList.addAll(paired.filterNot { it.address in recentAddresses }.sortedBy { it.name ?: it.address })
        pairedAdapter.notifyDataSetChanged()
        appendEventLog("PAIRED_DEVICES loaded=${pairedDevicesList.size}, recent=${recentDevicesList.size}")
        updateConnectionUi(connector.currentState)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        appendEventLog("ACTION: connect name=${device.name ?: "Unknown"} address=${device.address}")
        if (connector.currentMode != BluetoothMode.CLIENT) {
            connector.startClientMode()
            updateModeUi(BluetoothMode.CLIENT)
        }
        ensureSecurityLayer(BluetoothMode.CLIENT)
        Toast.makeText(this, "Connecting to: ${device.name ?: "Unknown"}", Toast.LENGTH_SHORT).show()
        securityLayer?.connect(device)
    }

    private fun updateBluetoothStatus() {
        if (!connector.isSupported()) {
            setStatus("Status: Bluetooth Not Supported", Color.RED)
            return
        }
        if (connector.isEnabled()) {
            setStatus("Status: Bluetooth On (Idle)", Color.parseColor("#4CAF50"))
        } else {
            setStatus("Status: Bluetooth Off", Color.GRAY)
        }
    }

    private fun setStatus(text: String, color: Int) {
        tvStatus.text = text
        viewStatusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun appendEventLog(message: String) {
        val log = "[${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())}] $message"
        runOnUiThread {
            if (!::tvEventLog.isInitialized) return@runOnUiThread
            tvEventLog.append("$log\n")
            scrollEventLog.post { scrollEventLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun updateConnectionUi(state: BluetoothConnectionState) {
        when (state) {
            BluetoothConnectionState.IDLE -> {
                when (connector.currentMode) {
                    BluetoothMode.CLIENT ->
                        setStatus("Status: Client Ready", Color.parseColor("#29B6F6"))
                    BluetoothMode.HOST ->
                        setStatus("Status: Host Ready", Color.parseColor("#FFB74D"))
                    BluetoothMode.P2P ->
                        setStatus("Status: P2P Ready", Color.parseColor("#AB47BC"))
                    BluetoothMode.NONE -> updateBluetoothStatus()
                }
            }
            BluetoothConnectionState.LISTENING -> {
                setStatus("Status: Host Listening...", Color.parseColor("#FFB74D"))
            }
            BluetoothConnectionState.CONNECTING -> {
                setStatus("Status: Client Connecting...", Color.parseColor("#29B6F6"))
            }
            BluetoothConnectionState.CONNECTED -> {
                val role = if (connector.currentMode == BluetoothMode.HOST) "Host" else "Client"
                setStatus("Status: $role Connected; authenticating...", Color.parseColor("#FFB74D"))
            }
            BluetoothConnectionState.DISCONNECTED -> {
                setStatus("Status: Disconnected", Color.RED)
            }
        }
    }

    private fun updateModeUi(mode: BluetoothMode) {
        val selectedColor = Color.parseColor("#1976D2")
        val defaultColor = Color.parseColor("#E0E0E0")
        val selectedTextColor = Color.WHITE
        val defaultTextColor = Color.parseColor("#212121")

        btnStopHost.visibility = if (mode == BluetoothMode.HOST) View.VISIBLE else View.GONE
        btnStopHost.isEnabled = mode == BluetoothMode.HOST

        // Keep the device groups visible when returning from Chat, even if the
        // connector has temporarily returned to NONE/IDLE.
        cardClientDevices.visibility = View.VISIBLE
        tvModeDescription.text = when (mode) {
            BluetoothMode.HOST -> "Host is discoverable and waits for a Client connection."
            BluetoothMode.CLIENT -> "Select a paired device or scan for a nearby Host."
            BluetoothMode.P2P -> "Automatically reconnect to the last authenticated peer."
            BluetoothMode.NONE -> "Choose Host to wait for a connection, or Client to find and connect to a device."
        }
    }

    private fun ensureSecurityLayer(mode: BluetoothMode, keepHostListening: Boolean = false) {
        val current = securityLayer
        if (current != null && securityRole == mode) {
            current.listener = securityListener
            return
        }
        current?.close()
        securityLayer = BluetoothSecurityLayer(connector, mode, securityEnabled, keepHostListening = keepHostListening).also {
            it.listener = securityListener
        }
        securityRole = mode
    }

    override fun onDestroy() {
        connector.stopDiscovery()
        securityLayer?.listener = null
        securityLayer?.close()
        securityLayer = null
        securityRole = null
        if (isFinishing && connector.currentState != BluetoothConnectionState.CONNECTED) {
            connector.disconnect()
            BluetoothConnector.sharedConnector = null
        }
        super.onDestroy()
    }

    // RecyclerView Adapter class
    private class DeviceAdapter(
        private val devices: List<BluetoothDevice>,
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bluetooth_device, parent, false)
            return DeviceViewHolder(view)
        }

        override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
            val device = devices[position]
            holder.bind(device)
            holder.itemView.setOnClickListener { onClick(device) }
        }

        override fun getItemCount(): Int = devices.size
    }

    private class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        private val tvDeviceAddress: TextView = view.findViewById(R.id.tvDeviceAddress)

        fun bind(device: BluetoothDevice) {
            tvDeviceName.text = device.name ?: "Unnamed Device"
            tvDeviceAddress.text = device.address
        }
    }
}
