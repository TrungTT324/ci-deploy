package hdisoft.app.logcat.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hdisoft.app.core.utils.DeviceUtils
import hdisoft.app.core.utils.NetworkUtils
import hdisoft.app.logcat.R
import hdisoft.app.logcat.di.LogcatServiceLocator
import hdisoft.app.logcat.domain.model.LogStreamConfig
import java.io.File
import java.util.Locale

class LogcatActivity : AppCompatActivity() {

    private lateinit var rvLogcat: RecyclerView
    private lateinit var etFilter: EditText
    private lateinit var btnToggleScroll: ImageButton // Toggle Auto Scroll Button
    private lateinit var btnStreamSettings: ImageButton // Stream Settings Button
    private lateinit var btnClear: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var btnRefresh: ImageButton // Play/Pause toggle
    private lateinit var pbLoading: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var tvStreamStatus: TextView // WebSocket status bar
    private lateinit var layoutUrlDisplay: View // Layout bar for connection URL
    private lateinit var tvFullUrl: TextView // Text displaying full connection URL
    private lateinit var layoutReadLogsWarning: View // Persistent banner when READ_LOGS isn't granted
    private lateinit var tvReadLogsCommand: TextView // adb command to copy

    private val logAdapter = LogAdapter()
    private var isAutoScrollEnabled = true

    private var logService: LogStreamService? = null
    private var isBound = false

    private val viewModel: LogcatViewModel by viewModels()

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as LogStreamService.LocalBinder
            logService = binder.getService()
            isBound = true
            setupServiceCallbacks()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            logService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogcatServiceLocator.init(this)
        setContentView(R.layout.activity_logcat)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbarLogcat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Bind Views
        rvLogcat = findViewById(R.id.rvLogcat)
        etFilter = findViewById(R.id.etFilter)
        btnToggleScroll = findViewById(R.id.btnToggleScroll)
        btnStreamSettings = findViewById(R.id.btnStreamSettings)
        btnClear = findViewById(R.id.btnClear)
        btnShare = findViewById(R.id.btnShare)
        btnRefresh = findViewById(R.id.btnRefresh)
        pbLoading = findViewById(R.id.pbLoading)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvStreamStatus = findViewById(R.id.tvStreamStatus)
        layoutUrlDisplay = findViewById(R.id.layoutUrlDisplay)
        tvFullUrl = findViewById(R.id.tvFullUrl)
        layoutReadLogsWarning = findViewById(R.id.layoutReadLogsWarning)
        tvReadLogsCommand = findViewById(R.id.tvReadLogsCommand)

        // Setup RecyclerView
        rvLogcat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true // Auto scroll to the end
        }
        rvLogcat.adapter = logAdapter

        // Filter Logic
        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                logAdapter.filter(s.toString())
                updateEmptyState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Click Listeners
        btnToggleScroll.setOnClickListener {
            isAutoScrollEnabled = !isAutoScrollEnabled
            updateAutoScrollButtonState()
        }

        btnStreamSettings.setOnClickListener {
            showStreamSettingsDialog()
        }

        btnRefresh.setOnClickListener {
            viewModel.toggleStream()
            val service = logService
            if (service != null) {
                // Sync service state
                if (service.isStreamingActive()) {
                    service.stopStream()
                    Toast.makeText(this, "Log stream paused", Toast.LENGTH_SHORT).show()
                } else {
                    service.startStream()
                    Toast.makeText(this, "Log stream resumed", Toast.LENGTH_SHORT).show()
                    pbLoading.visibility = View.VISIBLE
                    tvEmpty.visibility = View.GONE
                }
            }
        }

        btnClear.setOnClickListener {
            clearLogs()
        }

        btnShare.setOnClickListener {
            shareLogs()
        }

        // Copy Connection URL to clipboard listener
        layoutUrlDisplay.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("CI-Deploy Logcat URL", tvFullUrl.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied connection URL to clipboard", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Persistent warning banner
        tvReadLogsCommand.text = "adb shell pm grant $packageName android.permission.READ_LOGS"
        layoutReadLogsWarning.setOnClickListener {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("READ_LOGS grant command", tvReadLogsCommand.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied adb command to clipboard", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        updateReadLogsWarning()

        // Observe ViewModel
        observeViewModel()

        // Start and Bind Foreground Service
        val intent = Intent(this, LogStreamService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        updateAutoScrollButtonState(showToast = false)
        viewModel.loadConfig()
    }

    private fun observeViewModel() {
        viewModel.logLine.observe(this) { logLine ->
            pbLoading.visibility = View.GONE
            logAdapter.addLogLine(logLine)
            updateEmptyState()
            if (isAutoScrollEnabled && logAdapter.itemCount > 0) {
                rvLogcat.scrollToPosition(logAdapter.itemCount - 1)
            }
        }

        viewModel.statusText.observe(this) { status ->
            val config = viewModel.streamConfig.value ?: return@observe
            val protocolPrefix = config.protocol.name
            when (config.mode) {
                LogStreamConfig.Mode.DIRECT -> {
                    layoutUrlDisplay.visibility = View.GONE
                    tvStreamStatus.text = "Mode: Direct View (Local Logcat)"
                    tvStreamStatus.setTextColor(Color.parseColor("#64B5F6")) // Light Blue
                }
                LogStreamConfig.Mode.SERVER -> {
                    val localIp = NetworkUtils.getLocalIpAddress(this) ?: "127.0.0.1"
                    val clientCount = status.substringAfter("Clients: ").toIntOrNull() ?: 0
                    
                    val protocolScheme = when (config.protocol) {
                        LogStreamConfig.Protocol.WEBSOCKET -> "ws"
                        LogStreamConfig.Protocol.TCP -> "tcp"
                        LogStreamConfig.Protocol.UDP -> "udp"
                    }
                    val fullUrl = "$protocolScheme://$localIp:${config.port}"
                    tvFullUrl.text = fullUrl
                    layoutUrlDisplay.visibility = if (config.protocol == LogStreamConfig.Protocol.UDP) View.GONE else View.VISIBLE
                    
                    tvStreamStatus.text = "$protocolPrefix: Server Mode | Port: ${config.port} | Clients: $clientCount"
                    tvStreamStatus.setTextColor(Color.parseColor("#81C784")) // Light Green
                }
                LogStreamConfig.Mode.CLIENT -> {
                    layoutUrlDisplay.visibility = View.GONE
                    tvStreamStatus.text = "$protocolPrefix: $status"
                    if (status.contains("Connected") || status.contains("Sending")) {
                        tvStreamStatus.setTextColor(Color.parseColor("#81C784"))
                    } else if (status.contains("Error") || status.contains("Disconnected") || status.contains("stopped")) {
                        tvStreamStatus.setTextColor(Color.parseColor("#FF5252"))
                    } else {
                        tvStreamStatus.setTextColor(Color.parseColor("#FFB74D"))
                    }
                }
            }
        }

        viewModel.isStreaming.observe(this) { active ->
            if (active) {
                btnRefresh.setImageResource(android.R.drawable.ic_media_pause)
                btnRefresh.setColorFilter(Color.parseColor("#FF5252"))
                pbLoading.visibility = View.GONE
            } else {
                btnRefresh.setImageResource(android.R.drawable.ic_media_play)
                btnRefresh.setColorFilter(Color.parseColor("#81C784"))
                pbLoading.visibility = View.GONE
            }
        }

        viewModel.clearLogsDone.observe(this) { done ->
            if (done) {
                pbLoading.visibility = View.GONE
                logAdapter.clear()
                updateEmptyState()
                Toast.makeText(this, "Logcat buffer cleared", Toast.LENGTH_SHORT).show()
                viewModel.resetClearLogsFlag()
            }
        }

        viewModel.exportFile.observe(this) { file ->
            if (file != null) {
                pbLoading.visibility = View.GONE
                val authority = "$packageName.fileprovider"
                val uri = FileProvider.getUriForFile(this, authority, file)

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "CI-Deploy Logcat Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share Logcat via"))
                viewModel.resetExportFileFlag()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateReadLogsWarning()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateReadLogsWarning() {
        val granted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_LOGS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        // Rooted devices read full logcat via `su`, so the READ_LOGS grant isn't needed there.
        val needsWarning = !granted && !DeviceUtils.isDeviceRooted()
        layoutReadLogsWarning.visibility = if (needsWarning) View.VISIBLE else View.GONE
    }

    private fun setupServiceCallbacks() {
        val service = logService ?: return
        
        // Connect callbacks from service to viewmodel
        service.onLogReceived = { logLine ->
            viewModel.postLogLine(logLine)
        }

        service.onStatusChanged = { status ->
            viewModel.postStatus(status)
        }

        // Set initial status text
        viewModel.postStatus(service.getStreamStatusText())
        viewModel.loadConfig()
    }

    private fun showStreamSettingsDialog() {
        val config = viewModel.streamConfig.value ?: return

        val dp = resources.displayMetrics.density
        val scrollContainer = android.widget.ScrollView(this)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, p, p, p)
        }

        val tvModeLabel = android.widget.TextView(this).apply {
            text = "Log View Mode:"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        layout.addView(tvModeLabel)

        val radioGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        val rbDirect = android.widget.RadioButton(this).apply {
            text = "Direct View (Local)"
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        val rbServer = android.widget.RadioButton(this).apply {
            text = "Server Mode (Broadcast)"
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        val rbClient = android.widget.RadioButton(this).apply {
            text = "Client Mode (Send to Server)"
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        radioGroup.addView(rbDirect)
        radioGroup.addView(rbServer)
        radioGroup.addView(rbClient)
        layout.addView(radioGroup)

        when (config.mode) {
            LogStreamConfig.Mode.DIRECT -> rbDirect.isChecked = true
            LogStreamConfig.Mode.SERVER -> rbServer.isChecked = true
            LogStreamConfig.Mode.CLIENT -> rbClient.isChecked = true
        }

        val tvProtocolLabel = android.widget.TextView(this).apply {
            text = "Protocol:"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }
        layout.addView(tvProtocolLabel)

        val protocolRadioGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
        }
        val rbWebsocket = android.widget.RadioButton(this).apply {
            text = "WebSocket"
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        val rbTcp = android.widget.RadioButton(this).apply {
            text = "TCP Socket"
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        val rbUdp = android.widget.RadioButton(this).apply {
            text = "UDP Socket"
            setTextColor(Color.WHITE)
            id = View.generateViewId()
        }
        protocolRadioGroup.addView(rbWebsocket)
        protocolRadioGroup.addView(rbTcp)
        protocolRadioGroup.addView(rbUdp)
        layout.addView(protocolRadioGroup)

        when (config.protocol) {
            LogStreamConfig.Protocol.WEBSOCKET -> rbWebsocket.isChecked = true
            LogStreamConfig.Protocol.TCP -> rbTcp.isChecked = true
            LogStreamConfig.Protocol.UDP -> rbUdp.isChecked = true
        }

        val tvIpLabel = android.widget.TextView(this).apply {
            text = "Server IP Address (Client mode only):"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }
        layout.addView(tvIpLabel)

        val etIp = android.widget.EditText(this).apply {
            setText(config.ip)
            hint = "192.168.1.100"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            isEnabled = config.mode == LogStreamConfig.Mode.CLIENT
        }
        layout.addView(etIp)

        val tvPortLabel = android.widget.TextView(this).apply {
            text = "Port:"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }
        layout.addView(tvPortLabel)

        val etPort = android.widget.EditText(this).apply {
            setText(config.port.toString())
            hint = "8082"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(etPort)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            etIp.isEnabled = checkedId == rbClient.id
            val isDirect = checkedId == rbDirect.id
            rbWebsocket.isEnabled = !isDirect
            rbTcp.isEnabled = !isDirect
            rbUdp.isEnabled = !isDirect
        }

        val initialDirect = config.mode == LogStreamConfig.Mode.DIRECT
        rbWebsocket.isEnabled = !initialDirect
        rbTcp.isEnabled = !initialDirect
        rbUdp.isEnabled = !initialDirect

        scrollContainer.addView(layout)

        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Log View Settings")
            .setView(scrollContainer)
            .setPositiveButton("Save") { _, _ ->
                val selectedMode = when (radioGroup.checkedRadioButtonId) {
                    rbDirect.id -> LogStreamConfig.Mode.DIRECT
                    rbClient.id -> LogStreamConfig.Mode.CLIENT
                    else -> LogStreamConfig.Mode.SERVER
                }
                val selectedProtocol = when (protocolRadioGroup.checkedRadioButtonId) {
                    rbTcp.id -> LogStreamConfig.Protocol.TCP
                    rbUdp.id -> LogStreamConfig.Protocol.UDP
                    else -> LogStreamConfig.Protocol.WEBSOCKET
                }
                val ipStr = etIp.text.toString().trim()
                val portInt = etPort.text.toString().trim().toIntOrNull() ?: 8082

                viewModel.saveConfig(selectedMode, ipStr, portInt, selectedProtocol)
                Toast.makeText(this, "Settings saved. Restarting stream...", Toast.LENGTH_SHORT).show()

                val service = logService
                if (service != null) {
                    service.stopStream()
                    logAdapter.clear()
                    updateEmptyState()
                    service.startStream()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAutoScrollButtonState(showToast: Boolean = true) {
        if (isAutoScrollEnabled) {
            btnToggleScroll.setColorFilter(Color.parseColor("#64B5F6")) // Blue for active auto scroll
            if (showToast) {
                Toast.makeText(this, "Auto-scroll enabled", Toast.LENGTH_SHORT).show()
            }
            if (logAdapter.itemCount > 0) {
                rvLogcat.scrollToPosition(logAdapter.itemCount - 1)
            }
        } else {
            btnToggleScroll.setColorFilter(Color.parseColor("#888888")) // Grey for disabled auto scroll
            if (showToast) {
                Toast.makeText(this, "Auto-scroll disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearLogs() {
        pbLoading.visibility = View.VISIBLE
        viewModel.clearLogs()
    }

    private fun shareLogs() {
        val currentLogs = logAdapter.getFilteredLogs()
        if (currentLogs.isEmpty()) {
            Toast.makeText(this, "No logs to share", Toast.LENGTH_SHORT).show()
            return
        }

        pbLoading.visibility = View.VISIBLE
        viewModel.exportLogs(currentLogs, cacheDir)
    }

    private fun updateEmptyState() {
        if (logAdapter.itemCount == 0) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
        }
    }

    // Inner Adapter Class for log display
    private class LogAdapter : RecyclerView.Adapter<LogViewHolder>() {
        private val MAX_LOG_LINES = 2500
        private val allLogs = mutableListOf<String>()
        private var filteredLogs = mutableListOf<String>()
        private var currentQuery = ""

        fun getFilteredLogs(): List<String> = filteredLogs

        fun addLogLine(line: String) {
            allLogs.add(line)
            if (allLogs.size > MAX_LOG_LINES) {
                allLogs.removeAt(0)
            }

            val matchesFilter = currentQuery.isEmpty() ||
                    line.lowercase(Locale.getDefault()).contains(currentQuery.lowercase(Locale.getDefault()))

            if (matchesFilter) {
                filteredLogs.add(line)
                if (filteredLogs.size > MAX_LOG_LINES) {
                    filteredLogs.removeAt(0)
                    notifyItemRemoved(0)
                }
                notifyItemInserted(filteredLogs.size - 1)
            }
        }

        fun filter(query: String) {
            currentQuery = query
            applyFilter()
        }

        private fun applyFilter() {
            filteredLogs = if (currentQuery.isEmpty()) {
                allLogs.toMutableList()
            } else {
                val lowerQuery = currentQuery.lowercase(Locale.getDefault())
                allLogs.filter { it.lowercase(Locale.getDefault()).contains(lowerQuery) }.toMutableList()
            }
            notifyDataSetChanged()
        }

        fun clear() {
            allLogs.clear()
            filteredLogs.clear()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
            return LogViewHolder(view)
        }

        override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
            val logLine = filteredLogs[position]
            holder.bind(logLine)
        }

        override fun getItemCount(): Int = filteredLogs.size
    }

    private class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLogText: TextView = view.findViewById(R.id.tvLogText)

        fun bind(line: String) {
            tvLogText.text = line
            
            val color = when {
                line.contains(" E/") || line.contains(" E ") || line.contains(" F/") || line.contains(" F ") -> Color.parseColor("#FF5252") // Red
                line.contains(" W/") || line.contains(" W ") -> Color.parseColor("#FFB74D") // Orange
                line.contains(" I/") || line.contains(" I ") -> Color.parseColor("#81C784") // Green
                line.contains(" D/") || line.contains(" D ") -> Color.parseColor("#64B5F6") // Blue
                line.contains(" V/") || line.contains(" V ") -> Color.parseColor("#B0BEC5") // Grey
                else -> Color.parseColor("#E0E0E0") // Light Grey
            }
            tvLogText.setTextColor(color)
        }
    }
}
