package hdisoft.app.cideploy.features.main.presentation

import android.content.Intent
import hdisoft.app.cideploy.features.bluetooth.presentation.BluetoothTestActivity

/**
 * Bluetooth (`features/bluetooth`) integration for [MainActivity], split out
 * here per the same convention as `MainActivityWebServer.kt` — self-contained
 * slices of MainActivity become `internal fun MainActivity.xxx()` extension
 * functions in a sibling file rather than growing MainActivity.kt further.
 */

internal fun MainActivity.openBluetoothTest() {
    startActivity(Intent(this, BluetoothTestActivity::class.java))
}
