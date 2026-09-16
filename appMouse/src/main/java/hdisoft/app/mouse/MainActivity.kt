package hdisoft.app.mouse

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothHidMouseConnector
import hdisoft.app.cideploy.features.bluetooth.data.HidMouseState
import hdisoft.app.mouse.update.AppUpdateController

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var touchpad: MouseTouchpadView
    private lateinit var btnStartStop: Button
    private lateinit var btnDiscoverable: Button

    private val handler = Handler(Looper.getMainLooper())
    private var hidConnector: BluetoothHidMouseConnector? = null
    private var wantsStart = false
    private var scanModeReceiverRegistered = false

    // Reflects the adapter's actual visibility so the UI (and the
    // auto-discoverable retry below) always matches what other devices can
    // really see, instead of just what button was last tapped.
    private val scanModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) return
            if (isFinishing || isDestroyed) return
            val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.SCAN_MODE_NONE)
            onScanModeChanged(mode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
        }
    }

    /**
     * Bluetooth callbacks and broadcasts arrive on their own thread/timing and
     * get posted to the main [Handler]; by the time that runnable executes the
     * Activity may already be finishing/destroyed (e.g. the user backed out
     * right after tapping Start). Touching views or an
     * [androidx.activity.result.ActivityResultLauncher] at that point throws,
     * so every such callback must check this first.
     */
    private fun isActive(): Boolean = !isFinishing && !isDestroyed

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            proceedAfterPermissions()
        } else {
            statusText.text = getString(R.string.status_permission_denied)
        }
    }

    private val requestEnableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            proceedAfterPermissions()
        } else {
            statusText.text = getString(R.string.status_bluetooth_disabled)
        }
    }

    private val requestDiscoverable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* no-op: BroadcastReceiver-free, connection state drives the UI */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        touchpad = findViewById(R.id.touchpad)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnDiscoverable = findViewById(R.id.btnDiscoverable)

        // Independent of the Bluetooth/HID support check below: even a
        // device too old for the mouse feature should still be able to pull
        // down a newer build.
        AppUpdateController(this).checkForUpdate()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            statusText.text = getString(R.string.status_bluetooth_unsupported)
            btnStartStop.isEnabled = false
            btnDiscoverable.isEnabled = false
            return
        }

        // Built (and its interface referenced) only once we know the platform
        // actually supports BluetoothHidDevice, so nothing here ever gets
        // loaded/verified below API 28.
        val hidCallback = object : BluetoothHidMouseConnector.Callback {
            override fun onStateChanged(state: HidMouseState) {
                handler.post { if (isActive()) renderState(state) }
            }

            override fun onHostConnected(device: BluetoothDevice) {
                val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
                handler.post {
                    if (isActive()) statusText.text = getString(R.string.status_connected_format, name)
                }
            }

            override fun onHostDisconnected() {
                handler.post {
                    if (isActive()) statusText.text = getString(R.string.status_disconnected)
                }
            }

            override fun onError(message: String) {
                handler.post {
                    if (isActive()) Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        hidConnector = BluetoothHidMouseConnector(this, hidCallback)

        registerReceiver(scanModeReceiver, IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED))
        scanModeReceiverRegistered = true

        btnStartStop.setOnClickListener {
            if (wantsStart) {
                stopMouse()
            } else {
                wantsStart = true
                ensureBluetoothReady()
            }
        }

        btnDiscoverable.setOnClickListener { requestDiscoverableMode() }

        touchpad.listener = object : MouseTouchpadView.Listener {
            override fun onMove(dx: Float, dy: Float) {
                hidConnector?.sendMouseReport(dx.toInt(), dy.toInt())
            }

            override fun onLeftClick() = clickButton(BluetoothHidMouseConnector.BUTTON_LEFT)

            override fun onRightClick() = clickButton(BluetoothHidMouseConnector.BUTTON_RIGHT)

            override fun onScroll(steps: Int) {
                hidConnector?.sendMouseReport(0, 0, wheel = steps * 3)
            }
        }

        findViewById<Button>(R.id.btnLeftClick).setOnClickListener {
            clickButton(BluetoothHidMouseConnector.BUTTON_LEFT)
        }
        findViewById<Button>(R.id.btnRightClick).setOnClickListener {
            clickButton(BluetoothHidMouseConnector.BUTTON_RIGHT)
        }
        findViewById<Button>(R.id.btnScrollUp).setOnClickListener {
            hidConnector?.sendMouseReport(0, 0, wheel = -3)
        }
        findViewById<Button>(R.id.btnScrollDown).setOnClickListener {
            hidConnector?.sendMouseReport(0, 0, wheel = 3)
        }
    }

    private fun clickButton(mask: Int) {
        hidConnector?.sendMouseReport(0, 0, buttons = mask)
        handler.postDelayed({ hidConnector?.sendMouseReport(0, 0, buttons = 0) }, 40)
    }

    private fun ensureBluetoothReady() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            statusText.text = getString(R.string.status_bluetooth_unsupported)
            wantsStart = false
            return
        }
        val permissions = requiredPermissions()
        if (permissions.isNotEmpty()) {
            requestPermissions.launch(permissions)
            return
        }
        if (!adapter.isEnabled) {
            requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        proceedAfterPermissions()
    }

    private fun proceedAfterPermissions() {
        if (!wantsStart) return
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled != true) {
            requestEnableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        hidConnector?.start()
    }

    private fun requestDiscoverableMode() {
        if (!isActive()) return
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        // Permissions must be confirmed before touching anything on the
        // adapter: reading scanMode alone calls into the Bluetooth service
        // and throws SecurityException on Android 12+ without BLUETOOTH_SCAN.
        val permissions = requiredPermissions()
        try {
            if (permissions.isNotEmpty()) {
                requestPermissions.launch(permissions)
                return
            }
            if (adapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) return
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                // 0 = stay discoverable indefinitely instead of the usual 120s
                // default, so the host has time to find and pair without the
                // user racing a countdown. Stop still turns it off explicitly.
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0)
            }
            requestDiscoverable.launch(intent)
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.status_permission_denied), Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            // Activity result launchers are unregistered once the Activity is
            // destroyed; an in-flight async callback can still reach here
            // right on that boundary. Nothing to recover — just don't crash.
        }
    }

    private fun onScanModeChanged(isDiscoverable: Boolean) {
        if (isDiscoverable) {
            val name = localAdapterName()
            statusText.text = if (name != null) {
                getString(R.string.status_discoverable_format, name)
            } else {
                getString(R.string.status_registered)
            }
        } else if (wantsStart && hidConnector?.connectedHost == null) {
            // The 5-minute visibility window expired before a host connected —
            // ask the system to make us discoverable again automatically.
            requestDiscoverableMode()
        }
    }

    private fun localAdapterName(): String? = try {
        BluetoothAdapter.getDefaultAdapter()?.name
    } catch (e: SecurityException) {
        null
    }

    private fun requiredPermissions(): Array<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyArray()
        val needed = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.BLUETOOTH_CONNECT
        }
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.BLUETOOTH_ADVERTISE
        }
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.BLUETOOTH_SCAN
        }
        return needed.toTypedArray()
    }

    private fun stopMouse() {
        wantsStart = false
        hidConnector?.stop()
        statusText.text = getString(R.string.status_idle)
    }

    private fun renderState(state: HidMouseState) {
        btnStartStop.text = if (state == HidMouseState.IDLE) getString(R.string.btn_start) else getString(R.string.btn_stop)
        when (state) {
            HidMouseState.IDLE -> if (!wantsStart) statusText.text = getString(R.string.status_idle)
            HidMouseState.REGISTERING -> statusText.text = getString(R.string.status_registering)
            HidMouseState.REGISTERED -> {
                statusText.text = getString(R.string.status_registered)
                // Registered as a HID app but not yet visible to other devices —
                // this is the whole point of pressing Start, so ask right away
                // instead of making the user find the separate button.
                if (wantsStart) requestDiscoverableMode()
            }
            HidMouseState.UNAVAILABLE -> statusText.text = getString(R.string.status_bluetooth_unsupported)
            HidMouseState.HOST_CONNECTED -> { /* onHostConnected sets the device name */ }
        }
    }

    override fun onDestroy() {
        if (scanModeReceiverRegistered) {
            unregisterReceiver(scanModeReceiver)
            scanModeReceiverRegistered = false
        }
        hidConnector?.stop()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
