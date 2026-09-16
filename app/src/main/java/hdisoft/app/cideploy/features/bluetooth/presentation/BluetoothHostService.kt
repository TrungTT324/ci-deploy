package hdisoft.app.cideploy.features.bluetooth.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import hdisoft.app.cideploy.R
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnector
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothMode
import hdisoft.app.cideploy.features.bluetooth.security.data.BluetoothSecurityLayer

class BluetoothHostService : Service() {
    private var security: BluetoothSecurityLayer? = null
    override fun onCreate() {
        super.onCreate()
        // Android 11 requires foreground promotion before doing long-running
        // Bluetooth work from a started service.
        createChannel()
        startForeground(9101, notification())
        val connector = BluetoothConnector.sharedConnector ?: BluetoothConnector(this).also { BluetoothConnector.sharedConnector = it }
        security = BluetoothSecurityLayer.sharedSecurityLayer ?: BluetoothSecurityLayer(connector, BluetoothMode.HOST, keepHostListening = true)
        security?.startListening()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onDestroy() { security?.close(); security?.disconnect(); security = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("bluetooth_host", "Bluetooth Host", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification = NotificationCompat.Builder(this, "bluetooth_host").setSmallIcon(R.mipmap.ic_launcher).setContentTitle("CI-Deploy Bluetooth Host").setContentText("Waiting for Client connection").setOngoing(true).build()
}
