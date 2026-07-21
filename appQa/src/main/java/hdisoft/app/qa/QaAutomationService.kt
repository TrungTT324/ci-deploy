package hdisoft.app.qa

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class QaAutomationService : Service() {

    companion object {
        const val ACTION_START = "hdisoft.app.qa.START"
        const val ACTION_STOP = "hdisoft.app.qa.STOP"
        
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIFICATION_ID = 8123
        private const val CHANNEL_ID = "qa_automation_channel"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var instance: QaAutomationService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var mediaProjection: MediaProjection? = null
    private var screenCaptureHelper: ScreenCaptureHelper? = null
    private var screenRecordHelper: ScreenRecordHelper? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        screenCaptureHelper = ScreenCaptureHelper(this)
        screenRecordHelper = ScreenRecordHelper(this)
        isRunning = true
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            setupForeground()
            
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            
            // Register callback to cleanly stop when session ends
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    mediaProjection = null
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))

            // Start HTTP Web Server
            try {
                hdisoft.app.webserver.SimpleHttpServer.scriptExecutor = object : hdisoft.app.webserver.SimpleHttpServer.ScriptExecutor {
                    override fun executeScript(scriptJson: String): String {
                        return ScriptTool.runScript(this@QaAutomationService, scriptJson)
                    }
                }
                val serverIntent = Intent(this, hdisoft.app.webserver.HttpWebServerService::class.java)
                androidx.core.content.ContextCompat.startForegroundService(this, serverIntent)
                Toast.makeText(this, "Webserver starting on port 8085...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            showFloatingOverlay()
        } else {
            Toast.makeText(this, "Failed to start MediaProjection session", Toast.LENGTH_SHORT).show()
            stopSelf()
        }

        return START_REDELIVER_INTENT
    }

    private fun setupForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QA Automation Background Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, QaAutomationService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("QA Automation Service Active")
            .setContentText("Tap overlay to capture screenshots or record screens.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", pendingStopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlay() {
        if (floatingView != null) return

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_control_layout, null)
        
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        windowManager?.addView(floatingView, layoutParams)

        // Setup dragging gestures
        val dragHandle = floatingView!!.findViewById<View>(R.id.drag_handle)
        dragHandle.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams!!.x
                        initialY = layoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                        return true
                    }
                }
                return false
            }
        })

        // Setup screenshot action
        val btnScreenshot = floatingView!!.findViewById<ImageButton>(R.id.btn_screenshot)
        btnScreenshot.setOnClickListener {
            takeScreenShot()
        }

        // Setup record screen action
        val btnRecord = floatingView!!.findViewById<ImageButton>(R.id.btn_record)
        btnRecord.setOnClickListener {
            toggleScreenRecording(btnRecord)
        }

        // Setup automation sequence action
        val btnAuto = floatingView!!.findViewById<Button>(R.id.btn_auto)
        btnAuto.setOnClickListener {
            executeAutoSequence()
        }

        // Navigation key relays
        val btnBack = floatingView!!.findViewById<ImageButton>(R.id.btn_back)
        btnBack.setOnClickListener {
            val success = QaAccessibilityService.instance?.performNavigation(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
            if (!success) showAccessibilityWarning()
        }

        val btnHome = floatingView!!.findViewById<ImageButton>(R.id.btn_home)
        btnHome.setOnClickListener {
            val success = QaAccessibilityService.instance?.performNavigation(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
            if (!success) showAccessibilityWarning()
        }

        // Close service action
        val btnClose = floatingView!!.findViewById<ImageButton>(R.id.btn_close)
        btnClose.setOnClickListener {
            stopSelf()
        }
    }

    private fun takeScreenShot() {
        val projection = mediaProjection
        if (projection == null) {
            Toast.makeText(this, "MediaProjection not active", Toast.LENGTH_SHORT).show()
            return
        }

        // Hide overlay so it's not in the screenshot
        floatingView?.visibility = View.GONE
        
        // Wait briefly for UI layout to refresh without the overlay
        Handler(Looper.getMainLooper()).postDelayed({
            screenCaptureHelper?.captureScreen(projection, object : ScreenCaptureHelper.CaptureCallback {
                override fun onCaptureSuccess(bitmap: Bitmap) {
                    val name = "QA_Screenshot_${System.currentTimeMillis()}.png"
                    MediaSaveHelper.saveBitmapToReports(this@QaAutomationService, bitmap, name)
                    val uri = MediaSaveHelper.saveBitmapToGallery(this@QaAutomationService, bitmap, name)
                    
                    Handler(Looper.getMainLooper()).post {
                        floatingView?.visibility = View.VISIBLE
                        if (uri != null) {
                            Toast.makeText(this@QaAutomationService, "Screenshot saved to Pictures/QAApp", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@QaAutomationService, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onCaptureError(e: Throwable) {
                    Handler(Looper.getMainLooper()).post {
                        floatingView?.visibility = View.VISIBLE
                        Toast.makeText(this@QaAutomationService, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }, 150)
    }

    private fun toggleScreenRecording(btnRecord: ImageButton) {
        val recorder = screenRecordHelper ?: return
        val projection = mediaProjection
        if (projection == null) {
            Toast.makeText(this, "MediaProjection not active", Toast.LENGTH_SHORT).show()
            return
        }

        if (recorder.isRecordingNow()) {
            // Stop recording
            val videoFile = recorder.stopRecording()
            btnRecord.setImageResource(R.drawable.ic_record_start)
            btnRecord.clearColorFilter()

            if (videoFile != null && videoFile.exists()) {
                    val name = "QA_Recording_${System.currentTimeMillis()}.mp4"
                    MediaSaveHelper.saveVideoToReports(this, videoFile, name)
                    val uri = MediaSaveHelper.saveVideoToGallery(this, videoFile, name)
                if (uri != null) {
                    Toast.makeText(this, "Video saved to Movies/QAApp", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show()
                }
                videoFile.delete() // Clean up temp file
            }
        } else {
            // Start recording
            val tempFile = File(cacheDir, "temp_rec.mp4")
            
            // Hide overlay brief delay so start doesn't capture it
            floatingView?.visibility = View.GONE
            
            Handler(Looper.getMainLooper()).postDelayed({
                recorder.startRecording(projection, tempFile) { success, error ->
                    floatingView?.visibility = View.VISIBLE
                    if (success) {
                        btnRecord.setImageResource(R.drawable.ic_record_stop)
                        btnRecord.clearColorFilter()
                        Toast.makeText(this, "Recording started...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Recording failed: ${error?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 150)
        }
    }

    private fun executeAutoSequence() {
        val service = QaAccessibilityService.instance
        if (service == null) {
            showAccessibilityWarning()
            return
        }

        val projection = mediaProjection
        if (projection == null) {
            Toast.makeText(this, "MediaProjection not active", Toast.LENGTH_SHORT).show()
            return
        }

        // Run automated flow in main thread coroutine for clean timing
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(this@QaAutomationService, "Starting automation script...", Toast.LENGTH_SHORT).show()
            floatingView?.visibility = View.GONE
            
            // Step 1: Wait and click center
            delay(1500)
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val centerX = metrics.widthPixels / 2f
            val centerY = metrics.heightPixels / 2f
            
            service.clickAt(centerX, centerY)
            
            // Step 2: Wait and type text
            delay(1500)
            service.inputText("CI-Deploy QA Automation Active")
            
            // Step 3: Wait and click submit (slightly below center)
            delay(1500)
            service.clickAt(centerX, centerY + 200)

            // Step 4: Take automation report screenshot
            delay(1500)
            screenCaptureHelper?.captureScreen(projection, object : ScreenCaptureHelper.CaptureCallback {
                override fun onCaptureSuccess(bitmap: Bitmap) {
                    val name = "QA_AutoReport_${System.currentTimeMillis()}.png"
                    MediaSaveHelper.saveBitmapToReports(this@QaAutomationService, bitmap, name)
                    MediaSaveHelper.saveBitmapToGallery(this@QaAutomationService, bitmap, name)
                    Toast.makeText(this@QaAutomationService, "Auto-screenshot saved to Pictures", Toast.LENGTH_LONG).show()
                    floatingView?.visibility = View.VISIBLE
                }

                override fun onCaptureError(e: Throwable) {
                    Toast.makeText(this@QaAutomationService, "Screenshot error: ${e.message}", Toast.LENGTH_SHORT).show()
                    floatingView?.visibility = View.VISIBLE
                }
            })
            
            Toast.makeText(this@QaAutomationService, "Automation script finished!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAccessibilityWarning() {
        Toast.makeText(
            this,
            "QA Accessibility Service must be enabled to automate actions. Check app dashboard.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun hideFloatingOverlay() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
    }

    fun takeScreenShotExternal() {
        Handler(Looper.getMainLooper()).post {
            takeScreenShot()
        }
    }

    private fun updateRecordButtonUI(isRecording: Boolean) {
        val btnRecord = floatingView?.findViewById<ImageButton>(R.id.btn_record) ?: return
        if (isRecording) {
            btnRecord.setImageResource(R.drawable.ic_record_stop)
        } else {
            btnRecord.setImageResource(R.drawable.ic_record_start)
        }
        btnRecord.clearColorFilter()
    }

    fun startScreenRecordingExternal() {
        Handler(Looper.getMainLooper()).post {
            val recorder = screenRecordHelper ?: return@post
            val projection = mediaProjection ?: return@post
            if (recorder.isRecordingNow()) return@post

            val tempFile = File(cacheDir, "temp_rec.mp4")
            floatingView?.visibility = View.GONE
            
            Handler(Looper.getMainLooper()).postDelayed({
                recorder.startRecording(projection, tempFile) { success, error ->
                    floatingView?.visibility = View.VISIBLE
                    if (success) {
                        updateRecordButtonUI(true)
                        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to start recording: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 150)
        }
    }

    fun stopScreenRecordingExternal() {
        Handler(Looper.getMainLooper()).post {
            val recorder = screenRecordHelper ?: return@post
            if (!recorder.isRecordingNow()) return@post

            val videoFile = recorder.stopRecording()
            updateRecordButtonUI(false)

            if (videoFile != null && videoFile.exists()) {
                val name = "QA_Recording_${System.currentTimeMillis()}.mp4"
                MediaSaveHelper.saveVideoToReports(this, videoFile, name)
                val uri = MediaSaveHelper.saveVideoToGallery(this, videoFile, name)
                if (uri != null) {
                    Toast.makeText(this, "Video saved to Movies/QAApp", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to save video", Toast.LENGTH_SHORT).show()
                }
                videoFile.delete() // Clean up temp file
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        instance = null
        hdisoft.app.webserver.SimpleHttpServer.scriptExecutor = null
        if (screenRecordHelper?.isRecordingNow() == true) {
            screenRecordHelper?.stopRecording()
        }
        hideFloatingOverlay()
        mediaProjection?.stop()
        mediaProjection = null
        
        // Stop HTTP Web Server
        try {
            stopService(Intent(this, hdisoft.app.webserver.HttpWebServerService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        super.onDestroy()
    }
}
