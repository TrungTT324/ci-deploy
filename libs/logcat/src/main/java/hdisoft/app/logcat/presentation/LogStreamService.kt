package hdisoft.app.logcat.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import hdisoft.app.logcat.di.LogcatServiceLocator

class LogStreamService : Service() {

    private val binder = LocalBinder()
    private lateinit var logcatRepository: hdisoft.app.logcat.domain.repository.LogcatRepository

    private val CHANNEL_ID = "log_stream_channel"
    private val NOTIFICATION_ID = 8888

    var onLogReceived: ((String) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): LogStreamService = this@LogStreamService
    }

    override fun onCreate() {
        super.onCreate()
        LogcatServiceLocator.init(this)
        logcatRepository = LogcatServiceLocator.logcatRepository
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP_SERVICE") {
            stopStream()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!logcatRepository.isStreamingActive()) {
            startForeground(NOTIFICATION_ID, createNotification("Starting Log Streamer..."))
            startStream()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
    }

    fun isStreamingActive(): Boolean = logcatRepository.isStreamingActive()

    fun startStream() {
        logcatRepository.startStream(
            onLogReceived = { logLine ->
                onLogReceived?.invoke(logLine)
            },
            onStatusChanged = { status ->
                onStatusChanged?.invoke(status)
                updateNotification(status)
            }
        )
    }

    fun stopStream() {
        logcatRepository.stopStream()
    }

    fun getStreamStatusText(): String {
        return logcatRepository.getStreamStatusText()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Log Stream Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, LogcatActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val stopIntent = Intent(this, LogStreamService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CI-Deploy Log Streamer")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }
}
