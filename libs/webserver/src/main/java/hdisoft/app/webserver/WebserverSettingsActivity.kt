package hdisoft.app.webserver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.net.NetworkInterface
import java.util.Collections

class WebserverSettingsActivity : AppCompatActivity() {

    private lateinit var webConfig: WebConfig
    
    // Service binding
    private var webService: HttpWebServerService? = null
    private var isBound = false

    // UI elements
    private lateinit var cbRequireAuth: CheckBox
    private lateinit var tvPin: TextView
    private lateinit var btnGenPin: Button
    private lateinit var tvAccessUrlWifi: TextView
    
    // Status elements
    private lateinit var viewStatusDot: View
    private lateinit var tvStatusText: TextView
    private lateinit var btnToggleServer: Button

    // Log elements
    private lateinit var tvLogConsole: TextView
    private lateinit var scrollLogConsole: ScrollView

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HttpWebServerService.LocalBinder
            webService = binder.getService()
            isBound = true
            updateUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            webService = null
            isBound = false
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webConfig = WebConfig(this)
        
        // Build Layout Programmatically
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F5F5F7"))
        }

        // 1. Toolbar
        val toolbar = Toolbar(this).apply {
            title = "Webserver Settings"
            setTitleTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1976D2")) // Android Blue
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(resources.getIdentifier("action_bar_default_height", "dimen", "android"))
            )
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        rootLayout.addView(toolbar)

        // 2. ScrollView Container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
        }

        // --- SECTION: SERVER STATUS ---
        val tvStatusHeader = TextView(this).apply {
            text = "SERVER STATUS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.GRAY)
            setPadding(10, 10, 10, 10)
        }
        contentLayout.addView(tvStatusHeader)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(30, 30, 30, 30)
            elevation = 4f
        }

        // Status indicator dot
        viewStatusDot = View(this).apply {
            val size = 30
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = 20
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
        }
        statusCard.addView(viewStatusDot)

        tvStatusText = TextView(this).apply {
            text = "Checking status..."
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        statusCard.addView(tvStatusText)

        btnToggleServer = Button(this).apply {
            text = "START"
            textSize = 12f
            setOnClickListener {
                toggleServerState()
            }
        }
        statusCard.addView(btnToggleServer)
        contentLayout.addView(statusCard)

        // --- SECTION: CONFIGURATION ---
        val tvSettingsHeader = TextView(this).apply {
            text = "SECURITY CONFIGURATION"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.GRAY)
            setPadding(10, 30, 10, 10)
        }
        contentLayout.addView(tvSettingsHeader)

        // Settings Card
        val settingsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(30, 30, 30, 30)
            elevation = 4f
        }

        cbRequireAuth = CheckBox(this).apply {
            text = "Require PIN Authentication"
            textSize = 16f
            isChecked = webConfig.requireAuthen
            setOnCheckedChangeListener { _, isChecked ->
                webConfig.requireAuthen = isChecked
                webService?.setRequireAuthen(isChecked)
                updateUi()
            }
        }
        settingsCard.addView(cbRequireAuth)

        val pinLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 0)
        }

        tvPin = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pinLayout.addView(tvPin)

        btnGenPin = Button(this).apply {
            text = "GENERATE PIN"
            textSize = 12f
            setOnClickListener {
                webService?.generateNewPin()
                updateUi()
            }
        }
        pinLayout.addView(btnGenPin)
        settingsCard.addView(pinLayout)
        
        contentLayout.addView(settingsCard)

        // --- SECTION: LIVE LOGS ---
        val logHeaderLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10, 30, 10, 10)
        }
        val tvLogHeader = TextView(this).apply {
            text = "LIVE EVENT LOGS"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnClearLogs = TextView(this).apply {
            text = "🗑 CLEAR"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#D32F2F"))
            isClickable = true
            setOnClickListener {
                WebserverLogger.clear()
            }
        }
        logHeaderLayout.addView(tvLogHeader)
        logHeaderLayout.addView(btnClearLogs)
        contentLayout.addView(logHeaderLayout)

        // Log Console
        scrollLogConsole = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                400 // Fixed height for console (approx 150dp)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E1E1E")) // Dark gray console
                cornerRadius = 8f
            }
            setPadding(15, 15, 15, 15)
        }

        tvLogConsole = TextView(this).apply {
            text = "Console initialized..."
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#A9B7C6")) // Light text
        }
        scrollLogConsole.addView(tvLogConsole)
        contentLayout.addView(scrollLogConsole)

        // --- SECTION: ACCESS GUIDE ---
        val tvGuideHeader = TextView(this).apply {
            text = "ACCESS GUIDE"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.GRAY)
            setPadding(10, 40, 10, 10)
        }
        contentLayout.addView(tvGuideHeader)

        // Guide Card
        val guideCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(30, 30, 30, 30)
            elevation = 4f
        }

        // Method 1: Local Wifi
        val tvMethod1Title = TextView(this).apply {
            text = "Option 1: Connect via local Wi-Fi"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1976D2"))
            setPadding(0, 0, 0, 10)
        }
        guideCard.addView(tvMethod1Title)

        tvAccessUrlWifi = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#2E7D32")) // Green
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 30)
        }
        guideCard.addView(tvAccessUrlWifi)

        // Method 2: ADB USB
        val tvMethod2Title = TextView(this).apply {
            text = "Option 2: Connect via ADB (Emulator or USB)"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#1976D2"))
            setPadding(0, 10, 0, 10)
        }
        guideCard.addView(tvMethod2Title)

        val tvAdbCommandLabel = TextView(this).apply {
            text = "1. Run this command on your computer Terminal:"
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 5)
        }
        guideCard.addView(tvAdbCommandLabel)

        val tvAdbCommand = TextView(this).apply {
            text = "   adb forward tcp:${HttpWebServerService.defaultPort} tcp:${HttpWebServerService.defaultPort}"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            setPadding(15, 15, 15, 15)
        }
        guideCard.addView(tvAdbCommand)

        val tvAdbUrlLabel = TextView(this).apply {
            text = "2. Open your computer browser and access:"
            textSize = 13f
            setTextColor(Color.GRAY)
            setPadding(0, 20, 0, 5)
        }
        guideCard.addView(tvAdbUrlLabel)

        val tvAccessUrlUsb = TextView(this).apply {
            text = "👉 URL: http://localhost:${HttpWebServerService.defaultPort}"
            textSize = 14f
            setTextColor(Color.parseColor("#2E7D32"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 10)
        }
        guideCard.addView(tvAccessUrlUsb)

        contentLayout.addView(guideCard)
        scrollView.addView(contentLayout)
        rootLayout.addView(scrollView)
        
        setContentView(rootLayout)

        // Start observing webserver logs
        WebserverLogger.logs.observe(this) { logList ->
            val consoleText = logList.joinToString("\n")
            tvLogConsole.text = if (consoleText.isEmpty()) "Console cleared." else consoleText
            
            // Auto scroll console to bottom
            scrollLogConsole.post {
                scrollLogConsole.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, HttpWebServerService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun toggleServerState() {
        val service = webService ?: return
        if (service.isServerRunning()) {
            service.stopServer()
            Toast.makeText(this, "Webserver STOPPED", Toast.LENGTH_SHORT).show()
        } else {
            service.startServer()
            Toast.makeText(this, "Webserver STARTED", Toast.LENGTH_SHORT).show()
        }
        updateUi()
    }

    private fun updateUi() {
        val pin = webConfig.authPin
        val isAuthRequired = webConfig.requireAuthen
        
        tvPin.text = "Security PIN: $pin"
        tvPin.visibility = if (isAuthRequired) View.VISIBLE else View.GONE
        btnGenPin.visibility = if (isAuthRequired) View.VISIBLE else View.GONE
        
        val service = webService
        if (service != null && service.isServerRunning()) {
            // Running
            viewStatusDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50")) // Green
            }
            tvStatusText.text = "Server is RUNNING"
            btnToggleServer.text = "STOP"
            btnToggleServer.setBackgroundColor(Color.parseColor("#E53935")) // Red button
        } else {
            // Stopped
            viewStatusDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F44336")) // Red
            }
            tvStatusText.text = "Server is STOPPED"
            btnToggleServer.text = "START"
            btnToggleServer.setBackgroundColor(Color.parseColor("#4CAF50")) // Green button
        }

        val localIp = getLocalIpAddress() ?: "127.0.0.1"
        val port = webService?.getPort() ?: HttpWebServerService.defaultPort
        tvAccessUrlWifi.text = "👉 URL: http://$localIp:$port"
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val sAddr = addr.hostAddress
                        if (sAddr != null) {
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) return sAddr
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
