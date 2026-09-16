package hdisoft.app.cideploy.features.bluetooth.presentation

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import hdisoft.app.cideploy.R
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothMode

class BluetoothSettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bluetooth_peer", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_bluetooth_settings); supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val modes = findViewById<RadioGroup>(R.id.bluetoothModeGroup); val saved = prefs.getString("preferred_mode", BluetoothMode.NONE.name); modes.check(when (saved) { "HOST" -> R.id.modeHost; "CLIENT" -> R.id.modeClient; "P2P" -> R.id.modeP2p; else -> -1 })
        findViewById<SwitchCompat>(R.id.securitySwitch).isChecked = prefs.getBoolean("security_enabled", true)
        findViewById<EditText>(R.id.intervalInput).setText(prefs.getLong("retry_interval_ms", 1500L).toString())
        findViewById<Button>(R.id.saveBluetoothSettings).setOnClickListener { val mode = when (modes.checkedRadioButtonId) { R.id.modeHost -> BluetoothMode.HOST; R.id.modeClient -> BluetoothMode.CLIENT; R.id.modeP2p -> BluetoothMode.P2P; else -> BluetoothMode.NONE }; prefs.edit().putString("preferred_mode", mode.name).putBoolean("security_enabled", findViewById<SwitchCompat>(R.id.securitySwitch).isChecked).putLong("retry_interval_ms", findViewById<EditText>(R.id.intervalInput).text.toString().toLongOrNull()?.coerceAtLeast(100L) ?: 1500L).apply(); setResult(RESULT_OK); finish() }
    }
    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
