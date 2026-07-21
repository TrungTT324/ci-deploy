package hdisoft.app.cideploy.features.bluetooth.data

import android.content.Context
data class StoredBluetoothMessage(val type: String, val value: String, val outgoing: Boolean, val time: Long)
class BluetoothMessageHistoryStore(context: Context, peerAddress: String) {
    private val prefs = context.applicationContext.getSharedPreferences("bluetooth_message_history", Context.MODE_PRIVATE)
    private val key = "messages_${peerAddress.replace(":", "_")}"
    fun add(type: String, value: String, outgoing: Boolean) { val rows = read().takeLast(199).toMutableList(); rows.add(StoredBluetoothMessage(type, value.replace("\n", " "), outgoing, System.currentTimeMillis())); prefs.edit().putString(key, rows.joinToString("\n") { listOf(it.type, it.outgoing, it.time, it.value).joinToString("|") }).apply() }
    fun read() = prefs.getString(key, "").orEmpty().lineSequence().mapNotNull { row -> val p = row.split('|', limit = 4); if (p.size == 4) p[2].toLongOrNull()?.let { StoredBluetoothMessage(p[0], p[3], p[1].toBoolean(), it) } else null }.toList()
}
