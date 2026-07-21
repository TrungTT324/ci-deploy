package hdisoft.app.cideploy.features.bluetooth.data

import android.content.Context

class BluetoothPeerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bluetooth_peer", Context.MODE_PRIVATE)
    fun save(device: android.bluetooth.BluetoothDevice) {
        prefs.edit().putString("address", device.address).putString("name", device.name).apply()
    }
    fun address(): String? = prefs.getString("address", null)
    fun name(): String? = prefs.getString("name", null)
    fun clear() = prefs.edit().clear().apply()
}
