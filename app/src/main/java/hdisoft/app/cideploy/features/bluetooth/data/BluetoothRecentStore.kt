package hdisoft.app.cideploy.features.bluetooth.data

import android.bluetooth.BluetoothDevice
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BluetoothRecentConnection(val name: String, val address: String, val mode: String, val connectedAt: Long)

class BluetoothRecentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bluetooth_peer", Context.MODE_PRIVATE)
    fun add(device: BluetoothDevice, mode: BluetoothMode) {
        val rows = recent().filterNot { it.address == device.address }.toMutableList()
        rows.add(0, BluetoothRecentConnection(device.name ?: "Unknown", device.address, mode.name, System.currentTimeMillis()))
        prefs.edit().putString("recent_connections", rows.take(20).joinToString("\n") { listOf(it.name, it.address, it.mode, it.connectedAt).joinToString("|") }).apply()
    }
    fun recent() = prefs.getString("recent_connections", "").orEmpty().lineSequence().mapNotNull {
        val p = it.split('|'); if (p.size == 4) p[3].toLongOrNull()?.let { t -> BluetoothRecentConnection(p[0], p[1], p[2], t) } else null
    }.sortedByDescending { it.connectedAt }.toList()
    companion object { fun formatTime(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(t)) }
}
