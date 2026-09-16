package hdisoft.app.webserver

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder

class HttpWebServerService : Service() {

    companion object {
        @Volatile
        var isRunning = false
            private set

        /**
         * Port the server binds to if the app hasn't persisted a different
         * one via [WebConfig]. Integrating apps set this once, before ever
         * starting [HttpWebServerService] (e.g. in `Application.onCreate()`
         * or the launching Activity's `onCreate()`) — useful when more than
         * one app using this module might run on the same device at once
         * and would otherwise collide on the same default port.
         */
        var defaultPort: Int = 8085
    }

    private val binder = LocalBinder()
    private var httpServer: SimpleHttpServer? = null
    private var port: Int = defaultPort
    lateinit var webConfig: WebConfig

    inner class LocalBinder : Binder() {
        fun getService(): HttpWebServerService = this@HttpWebServerService
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        webConfig = WebConfig(this)
        port = webConfig.getPort(defaultPort)

        // Start as Foreground Service to run in background on Android 8.0+
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(9985, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(9985, notification)
        }

        startServer()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "webserver_channel",
                "Web Server Status",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of the local report web server"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, "webserver_channel")
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }

        return builder
            .setContentTitle("QA Web Server Active")
            .setContentText("Serving reports on port $port")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun startServer() {
        if (httpServer == null) {
            httpServer = SimpleHttpServer(this, port, webConfig).apply {
                start()
            }
            WebserverLogger.log("Webserver STARTED on port $port")
        }
    }

    fun stopServer() {
        if (httpServer != null) {
            httpServer?.stop()
            httpServer = null
            WebserverLogger.log("Webserver STOPPED")
        }
    }

    fun isServerRunning(): Boolean = httpServer != null

    fun getPort(): Int = port

    fun getPin(): String = webConfig.authPin

    fun isRequireAuthen(): Boolean = webConfig.requireAuthen

    fun setRequireAuthen(value: Boolean) {
        webConfig.requireAuthen = value
        WebserverLogger.log("Authentication requirement set to: $value")
    }

    fun generateNewPin(): String {
        val newPin = webConfig.generateNewPin()
        WebserverLogger.log("New Security PIN generated: $newPin")
        return newPin
    }
}
