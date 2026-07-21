package hdisoft.app.cideploy.features.bluetooth.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor
import java.util.concurrent.Executors

enum class HidMouseState {
    IDLE,
    REGISTERING,
    REGISTERED,
    HOST_CONNECTED,
    UNAVAILABLE
}

/**
 * Wraps the platform Bluetooth HID Device Profile (API 28+) so this device can
 * register itself as a standard Bluetooth mouse peripheral: any paired host
 * (PC, tablet, phone) sees it exactly like a real HID mouse, no companion app
 * required on the host side.
 */
@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("MissingPermission")
class BluetoothHidMouseConnector(
    context: Context,
    var callback: Callback? = null
) {

    interface Callback {
        fun onStateChanged(state: HidMouseState)
        fun onHostConnected(device: BluetoothDevice)
        fun onHostDisconnected()
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "BluetoothHidMouse"

        const val REPORT_ID_MOUSE: Byte = 1

        const val BUTTON_LEFT = 0x01
        const val BUTTON_RIGHT = 0x02
        const val BUTTON_MIDDLE = 0x04

        // Standard relative-mouse HID report descriptor: report ID, 3 button
        // bits + 5 padding bits, then signed 8-bit relative X / Y / wheel.
        private val MOUSE_DESCRIPTOR: ByteArray = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_MOUSE, //   Report ID (1)
            0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
            0x05.toByte(), 0x09.toByte(), //     Usage Page (Buttons)
            0x19.toByte(), 0x01.toByte(), //     Usage Minimum (Button 1)
            0x29.toByte(), 0x03.toByte(), //     Usage Maximum (Button 3)
            0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x75.toByte(), 0x01.toByte(), //     Report Size (1)
            0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute) - 3 button bits
            0x95.toByte(), 0x01.toByte(), //     Report Count (1)
            0x75.toByte(), 0x05.toByte(), //     Report Size (5)
            0x81.toByte(), 0x03.toByte(), //     Input (Constant) - 5 bit padding
            0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //     Usage (X)
            0x09.toByte(), 0x31.toByte(), //     Usage (Y)
            0x09.toByte(), 0x38.toByte(), //     Usage (Wheel)
            0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), //     Report Size (8)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
            0xC0.toByte(),                //   End Collection
            0xC0.toByte()                 // End Collection
        )
    }

    private val appContext = context.applicationContext
    private val executor: Executor = Executors.newSingleThreadExecutor()

    private var hidDevice: BluetoothHidDevice? = null
    private var registered = false

    var connectedHost: BluetoothDevice? = null
        private set

    var currentState: HidMouseState = HidMouseState.IDLE
        private set(value) {
            if (field != value) {
                field = value
                callback?.onStateChanged(value)
            }
        }

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "HDI Soft Mouse",
        "Virtual Bluetooth mouse",
        "hdisoft",
        BluetoothHidDevice.SUBCLASS1_MOUSE,
        MOUSE_DESCRIPTOR
    )

    private val outQosSettings = BluetoothHidDeviceAppQosSettings(
        BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
        800, 9, 0, 11250, 11250
    )

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@BluetoothHidMouseConnector.registered = registered
            currentState = if (registered) HidMouseState.REGISTERED else HidMouseState.IDLE
            if (!registered) {
                callback?.onError("HID mouse app failed to register")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    currentState = HidMouseState.HOST_CONNECTED
                    device?.let { callback?.onHostConnected(it) }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost == device) connectedHost = null
                    currentState = if (registered) HidMouseState.REGISTERED else HidMouseState.IDLE
                    callback?.onHostDisconnected()
                }
                else -> {}
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            try {
                hidDevice?.replyReport(device, type, id, byteArrayOf(REPORT_ID_MOUSE, 0, 0, 0, 0))
            } catch (e: SecurityException) {
                Log.e(TAG, "replyReport failed", e)
            }
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // No writable feature/output reports for a plain mouse.
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            // Not used: this device only sends input reports.
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            connectedHost = null
            currentState = if (registered) HidMouseState.REGISTERED else HidMouseState.IDLE
        }
    }

    private var bondReceiverRegistered = false

    // Some hosts (notably macOS) bond at the ACL link level but never open
    // the HID Device Profile channel on their own — the peripheral is
    // expected to request that connection explicitly once bonded, instead
    // of only waiting passively for onConnectionStateChanged. Without this,
    // the host shows as "paired"/"connected" while every sendReport() is
    // silently a no-op because connectedHost never gets set.
    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            if (bondState != BluetoothDevice.BOND_BONDED) return
            try {
                hidDevice?.connect(device)
            } catch (e: SecurityException) {
                callback?.onError("Missing permission to connect: ${e.message}")
            }
        }
    }

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as BluetoothHidDevice
            hidDevice = hid
            currentState = HidMouseState.REGISTERING
            try {
                hid.registerApp(sdpSettings, null, outQosSettings, executor, hidCallback)
            } catch (e: SecurityException) {
                currentState = HidMouseState.UNAVAILABLE
                callback?.onError("Missing Bluetooth permission: ${e.message}")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            registered = false
            connectedHost = null
            currentState = HidMouseState.IDLE
        }
    }

    fun isSupported(): Boolean = BluetoothAdapter.getDefaultAdapter() != null

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Registers this app as a HID mouse device and waits for a host to connect. */
    fun start() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            currentState = HidMouseState.UNAVAILABLE
            callback?.onError("Bluetooth is not supported on this device")
            return
        }
        if (!hasPermissions()) {
            callback?.onError("Missing BLUETOOTH_CONNECT permission")
            return
        }
        if (hidDevice != null) return
        if (!bondReceiverRegistered) {
            appContext.registerReceiver(bondStateReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            bondReceiverRegistered = true
        }
        try {
            currentState = HidMouseState.REGISTERING
            adapter.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE)
        } catch (e: SecurityException) {
            currentState = HidMouseState.UNAVAILABLE
            callback?.onError("Missing Bluetooth permission: ${e.message}")
        }
    }

    /** Unregisters the HID app and releases the profile proxy. */
    fun stop() {
        if (bondReceiverRegistered) {
            try {
                appContext.unregisterReceiver(bondStateReceiver)
            } catch (e: IllegalArgumentException) {
                // Already unregistered; ignore.
            }
            bondReceiverRegistered = false
        }
        val adapter = BluetoothAdapter.getDefaultAdapter()
        val hid = hidDevice
        try {
            connectedHost?.let { hid?.disconnect(it) }
            if (registered) hid?.unregisterApp()
        } catch (e: SecurityException) {
            Log.e(TAG, "unregisterApp failed", e)
        }
        if (hid != null && adapter != null) {
            adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hidDevice = null
        connectedHost = null
        registered = false
        currentState = HidMouseState.IDLE
    }

    /**
     * Sends one relative HID mouse report. [dx]/[dy]/[wheel] are clamped to the
     * signed byte range the descriptor declares; [buttons] is a bitmask of
     * [BUTTON_LEFT]/[BUTTON_RIGHT]/[BUTTON_MIDDLE].
     */
    fun sendMouseReport(dx: Int, dy: Int, buttons: Int = 0, wheel: Int = 0): Boolean {
        val hid = hidDevice ?: return false
        val device = connectedHost ?: return false
        val report = byteArrayOf(
            (buttons and 0x07).toByte(),
            dx.coerceIn(-127, 127).toByte(),
            dy.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
        )
        return try {
            hid.sendReport(device, REPORT_ID_MOUSE.toInt(), report)
        } catch (e: SecurityException) {
            callback?.onError("Missing permission to send report: ${e.message}")
            false
        }
    }
}
