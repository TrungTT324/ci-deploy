package hdisoft.app.qa

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusAccessibility: TextView
    private lateinit var statusOverlay: TextView
    private lateinit var statusService: TextView
    private lateinit var descService: TextView
    private lateinit var statusWebserver: TextView
    private lateinit var descWebserver: TextView
    private lateinit var urlWebserver: TextView

    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnStartWebserver: Button
    private lateinit var btnStopWebserver: Button

    private val handler = Handler(Looper.getMainLooper())
    
    // Status polling runnable to update indicators when user changes settings and returns to app
    private val statusUpdateRunnable = object : Runnable {
        override fun run() {
            updatePermissionStatuses()
            handler.postDelayed(this, 1000)
        }
    }

    // MediaProjection capture launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val startIntent = Intent(this, QaAutomationService::class.java).apply {
                action = QaAutomationService.ACTION_START
                putExtra(QaAutomationService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(QaAutomationService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(startIntent)
            } else {
                startService(startIntent)
            }
            Toast.makeText(this, "QA Automation Service launched", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Media projection permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // appQa's own default — distinct from CI-Deploy's (8085) so both apps
        // can run their web console simultaneously on the same device without
        // colliding on the same port. Must be set before HttpWebServerService
        // is ever started.
        hdisoft.app.webserver.HttpWebServerService.defaultPort = 8086

        setContentView(R.layout.activity_main)

        // Initialize Views
        statusAccessibility = findViewById(R.id.status_accessibility)
        statusOverlay = findViewById(R.id.status_overlay)
        statusService = findViewById(R.id.status_service)
        descService = findViewById(R.id.desc_service)
        statusWebserver = findViewById(R.id.status_webserver)
        descWebserver = findViewById(R.id.desc_webserver)
        urlWebserver = findViewById(R.id.url_webserver)

        btnAccessibility = findViewById(R.id.btn_grant_accessibility)
        btnOverlay = findViewById(R.id.btn_grant_overlay)
        btnStart = findViewById(R.id.btn_start_service)
        btnStop = findViewById(R.id.btn_stop_service)
        btnStartWebserver = findViewById(R.id.btn_start_webserver)
        btnStopWebserver = findViewById(R.id.btn_stop_webserver)

        // Setup Button Listeners
        btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find 'appQa' in settings and enable it.", Toast.LENGTH_LONG).show()
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission granted on this version.", Toast.LENGTH_SHORT).show()
            }
        }

        btnStart.setOnClickListener {
            // Check Overlay permission first
            val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }

            if (!canOverlay) {
                Toast.makeText(this, "Please grant Overlay permission first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Launch Screen Capture approval flow
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
                mediaProjectionManager.createScreenCaptureIntent(config)
            } else {
                mediaProjectionManager.createScreenCaptureIntent()
            }
            mediaProjectionLauncher.launch(captureIntent)
        }

        btnStop.setOnClickListener {
            val stopIntent = Intent(this, QaAutomationService::class.java).apply {
                action = QaAutomationService.ACTION_STOP
            }
            startService(stopIntent)
            Toast.makeText(this, "QA Automation Service stopped", Toast.LENGTH_SHORT).show()
        }

        btnStartWebserver.setOnClickListener {
            val serverIntent = Intent(this, hdisoft.app.webserver.HttpWebServerService::class.java)
            ContextCompat.startForegroundService(this, serverIntent)
            Toast.makeText(this, "Web Server started", Toast.LENGTH_SHORT).show()
        }

        btnStopWebserver.setOnClickListener {
            stopService(Intent(this, hdisoft.app.webserver.HttpWebServerService::class.java))
            Toast.makeText(this, "Web Server stopped", Toast.LENGTH_SHORT).show()
        }

        // Ask for Notification Permissions on API 33+ (required for stable Foreground Services)
        checkNotificationPermission()

        // Auto-start Web Server on launch
        try {
            val serverIntent = Intent(this, hdisoft.app.webserver.HttpWebServerService::class.java)
            ContextCompat.startForegroundService(this, serverIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Auto-grant permissions if rooted device
        autoGrantPermissionsIfRooted()
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusUpdateRunnable)
    }

    private fun updatePermissionStatuses() {
        // 1. Accessibility Service check
        if (QaAccessibilityService.isEnabled()) {
            statusAccessibility.text = "ENABLED"
            statusAccessibility.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            statusAccessibility.text = "DISABLED"
            statusAccessibility.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // 2. Overlay permission check
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (hasOverlay) {
            statusOverlay.text = "GRANTED"
            statusOverlay.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            statusOverlay.text = "DENIED"
            statusOverlay.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // 3. QA background Service check
        if (QaAutomationService.isRunning) {
            statusService.text = "RUNNING"
            statusService.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            statusService.text = "STOPPED"
            statusService.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // 4. Web Report Server check
        if (isWebServerRunning()) {
            statusWebserver.text = "ONLINE"
            statusWebserver.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            urlWebserver.text = "URL: http://${getLocalIpAddress()}:${hdisoft.app.webserver.HttpWebServerService.defaultPort}/reports"
            urlWebserver.setTextColor(ContextCompat.getColor(this, R.color.qa_primary))
        } else {
            statusWebserver.text = "OFFLINE"
            statusWebserver.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            urlWebserver.text = "URL: http://<device_ip>:${hdisoft.app.webserver.HttpWebServerService.defaultPort}/reports"
            urlWebserver.setTextColor(ContextCompat.getColor(this, R.color.qa_text_secondary))
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!hasNotificationPermission) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
            }
        }
    }

    private fun getLocalIpAddress(): String {
        return hdisoft.app.core.utils.NetworkUtils.getLocalIpAddress(this) ?: "127.0.0.1"
    }

    private fun isWebServerRunning(): Boolean {
        return hdisoft.app.webserver.HttpWebServerService.isRunning
    }

    private fun autoGrantPermissionsIfRooted() {
        if (!isDeviceRooted()) return

        try {
            val process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)

            // 1. Grant Accessibility Service safely (without overriding other services)
            val currentServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val accService = "$packageName/hdisoft.app.qa.QaAccessibilityService"
            val newServices = if (currentServices.isEmpty()) {
                accService
            } else if (!currentServices.contains(accService)) {
                "$currentServices:$accService"
            } else {
                currentServices
            }
            os.writeBytes("settings put secure enabled_accessibility_services $newServices\n")
            os.writeBytes("settings put secure accessibility_enabled 1\n")

            // 2. Grant Overlay Permission
            os.writeBytes("appops set $packageName SYSTEM_ALERT_WINDOW allow\n")

            // 3. Grant Notification Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                os.writeBytes("pm grant $packageName android.permission.POST_NOTIFICATIONS\n")
            }

            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()

            Toast.makeText(this, "Root detected: Automatically enabled all permissions!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to auto-grant root permissions: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isDeviceRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        try {
            val file = java.io.File("/system/app/Superuser.apk")
            if (file.exists()) return true
        } catch (e: Exception) {}

        val paths = arrayOf(
            "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su",
            "/data/local/bin/su", "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            try {
                if (java.io.File(path).exists()) return true
            } catch (e: Exception) {}
        }
        return false
    }
}
