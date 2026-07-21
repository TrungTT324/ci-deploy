package hdisoft.app.cideploy.features.testtcp.presentation

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import hdisoft.app.cideploy.R
import hdisoft.app.core.net.TcpClient
import hdisoft.app.core.net.TcpPortScanner
import hdisoft.app.core.net.TcpServer
import hdisoft.app.core.utils.DeviceUtils
import hdisoft.app.core.utils.NetworkUtils
import hdisoft.app.logcat.data.datasource.LocalLogcatDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standalone screen to exercise core.net's TcpClient/TcpServer directly:
 * pick a role, start/stop, and watch connect/disconnect/data events as a live log.
 */
class TestTcpActivity : AppCompatActivity() {

    private lateinit var radioGroupMode: RadioGroup
    private lateinit var rbClient: RadioButton
    private lateinit var rbServer: RadioButton
    private lateinit var etIp: EditText
    private lateinit var etPort: EditText
    private lateinit var btnScan: Button
    private lateinit var cbAutoReconnect: CheckBox
    private lateinit var btnStartStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var etSendMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var tvLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var btnClearLog: ImageButton
    private lateinit var btnStreamLogcat: Button

    private val tcpServer = TcpServer()
    private val tcpClient = TcpClient()
    private val localLogcatDataSource = LocalLogcatDataSource()
    private var isRunning = false
    private var isStreamingLogcat = false
    private var logcatJob: Job? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_tcp)

        val toolbar: Toolbar = findViewById(R.id.toolbarTestTcp)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        radioGroupMode = findViewById(R.id.radioGroupMode)
        rbClient = findViewById(R.id.rbClient)
        rbServer = findViewById(R.id.rbServer)
        etIp = findViewById(R.id.etIp)
        etPort = findViewById(R.id.etPort)
        btnScan = findViewById(R.id.btnScan)
        cbAutoReconnect = findViewById(R.id.cbAutoReconnect)
        btnStartStop = findViewById(R.id.btnStartStop)
        tvStatus = findViewById(R.id.tvStatus)
        etSendMessage = findViewById(R.id.etSendMessage)
        btnSend = findViewById(R.id.btnSend)
        tvLog = findViewById(R.id.tvLog)
        scrollLog = findViewById(R.id.scrollLog)
        btnClearLog = findViewById(R.id.btnClearLog)
        btnStreamLogcat = findViewById(R.id.btnStreamLogcat)

        tvLog.movementMethod = ScrollingMovementMethod()

        radioGroupMode.setOnCheckedChangeListener { _, _ ->
            updateUiEnabled()
            updateStatus()
        }

        btnStartStop.setOnClickListener {
            if (isRunning) stopTest() else startTest()
        }

        btnScan.setOnClickListener { scanForHost() }

        btnStreamLogcat.setOnClickListener {
            if (isStreamingLogcat) stopLogcatStream() else startLogcatStream()
        }

        btnSend.setOnClickListener {
            val text = etSendMessage.text.toString()
            if (text.isBlank()) return@setOnClickListener
            sendMessage(text)
            etSendMessage.setText("")
        }

        btnClearLog.setOnClickListener { tvLog.text = "" }

        setupCallbacks()
        updateUiEnabled()
        updateStatus()
    }

    private fun setupCallbacks() {
        tcpServer.onClientConnected = { socket ->
            runOnUiThread {
                appendLog("[SYSTEM] Client connected: ${socket.inetAddress?.hostAddress}:${socket.port} (total: ${tcpServer.clientCount})")
                updateStatus()
            }
        }
        tcpServer.onClientDisconnected = { socket ->
            runOnUiThread {
                appendLog("[SYSTEM] Client disconnected: ${socket.inetAddress?.hostAddress}:${socket.port} (total: ${tcpServer.clientCount})")
                updateStatus()
            }
        }
        tcpServer.onLineReceived = { line ->
            runOnUiThread { appendLog("RECV: $line") }
        }
        tcpServer.onError = { msg ->
            runOnUiThread {
                appendLog("[SYSTEM] Server error: $msg")
                isRunning = false
                updateUiEnabled()
                updateStatus()
            }
        }

        tcpClient.onConnected = {
            runOnUiThread {
                appendLog("[SYSTEM] Connected")
                updateStatus()
            }
        }
        tcpClient.onDisconnected = {
            runOnUiThread {
                appendLog("[SYSTEM] Disconnected")
                updateStatus()
            }
        }
        tcpClient.onLineReceived = { line ->
            runOnUiThread { appendLog("RECV: $line") }
        }
        tcpClient.onError = { msg ->
            runOnUiThread { appendLog("[SYSTEM] Connection error: $msg") }
        }
    }

    private fun startTest() {
        val port = etPort.text.toString().toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show()
            return
        }

        if (rbServer.isChecked) {
            isRunning = true
            appendLog("[SYSTEM] Starting TCP Server on port $port...")
            tcpServer.start(port, welcomeMessage = "[SYSTEM]: Connected to Test TCP Server\n")
        } else {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Enter server IP", Toast.LENGTH_SHORT).show()
                return
            }
            isRunning = true
            appendLog("[SYSTEM] Connecting to $ip:$port (auto-reconnect: ${cbAutoReconnect.isChecked})...")
            tcpClient.connect(ip, port, autoReconnect = cbAutoReconnect.isChecked)
        }
        updateUiEnabled()
        updateStatus()
    }

    private var isScanning = false

    private fun scanForHost() {
        if (isScanning) return
        val port = etPort.text.toString().toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show()
            return
        }
        val subnet = NetworkUtils.getLocalSubnet(this)
        if (subnet == null) {
            Toast.makeText(this, "Connect to Wi-Fi to scan", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        updateUiEnabled()
        appendLog("[SYSTEM] Scanning LAN ${subnet}0-254 for a host with port $port open...")

        lifecycleScope.launch {
            val foundIp = TcpPortScanner.scan(subnet, port)
            isScanning = false
            updateUiEnabled()
            if (foundIp != null) {
                etIp.setText(foundIp)
                appendLog("[SYSTEM] Found host: $foundIp:$port")
            } else {
                appendLog("[SYSTEM] Scan finished: no host found on port $port")
                Toast.makeText(this@TestTcpActivity, "No host found on port $port", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopTest() {
        isRunning = false
        stopLogcatStream()
        tcpServer.stop()
        tcpClient.stop()
        appendLog("[SYSTEM] Stopped")
        updateUiEnabled()
        updateStatus()
    }

    /** Broadcasts this device's own logcat to whatever TCP clients are connected — lets
     * another device running this same screen in Client mode watch this device's logcat live. */
    private fun startLogcatStream() {
        if (!isRunning || !rbServer.isChecked) {
            Toast.makeText(this, "Start the TCP Server first", Toast.LENGTH_SHORT).show()
            return
        }
        isStreamingLogcat = true
        updateUiEnabled()

        // These must reach connected clients too (not just this device's own log view) —
        // otherwise a client sees nothing at all with no indication of why: no confirmation
        // that streaming actually started, and no explanation if the logcat process fails
        // to launch or is restricted to this app's own (likely sparse) log lines.
        val rooted = DeviceUtils.isDeviceRooted()
        val startMsg = if (rooted) {
            "[SYSTEM] Streaming full device logcat (root)...\n"
        } else {
            "[SYSTEM] Streaming logcat — device not rooted, so only this app's own log lines " +
                "are visible (Android restricts full logcat to root/system apps). Output may be sparse.\n"
        }
        appendLog(startMsg.removeSuffix("\n"))
        tcpServer.broadcast(startMsg)

        logcatJob = lifecycleScope.launch(Dispatchers.IO) {
            val process = localLogcatDataSource.getLogcatProcess()
            if (process == null) {
                val errorMsg = "[SYSTEM] Failed to start logcat process (check root/su access).\n"
                tcpServer.broadcast(errorMsg)
                withContext(Dispatchers.Main) {
                    appendLog(errorMsg.removeSuffix("\n"))
                    isStreamingLogcat = false
                    updateUiEnabled()
                }
                return@launch
            }
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    line?.let { tcpServer.broadcast(it + "\n") }
                }
            } catch (e: Exception) {
                val errorMsg = "[SYSTEM] Logcat stream error: ${e.message}\n"
                tcpServer.broadcast(errorMsg)
                withContext(Dispatchers.Main) { appendLog(errorMsg.removeSuffix("\n")) }
            } finally {
                try { process.destroy() } catch (e: Exception) {}
            }
        }
    }

    private fun stopLogcatStream() {
        if (!isStreamingLogcat) return
        isStreamingLogcat = false
        logcatJob?.cancel()
        logcatJob = null
        appendLog("[SYSTEM] Stopped streaming logcat")
        updateUiEnabled()
    }

    private fun sendMessage(text: String) {
        val sent = if (rbServer.isChecked) {
            tcpServer.broadcast(text + "\n")
            tcpServer.clientCount > 0
        } else {
            tcpClient.send(text + "\n")
        }
        appendLog(if (sent) "SEND: $text" else "[SYSTEM] Send failed (not connected)")
    }

    private fun updateUiEnabled() {
        btnStartStop.text = if (isRunning) "Stop" else "Start"
        btnStartStop.isEnabled = !isScanning
        rbClient.isEnabled = !isRunning
        rbServer.isEnabled = !isRunning
        etIp.isEnabled = !isRunning && !isScanning && rbClient.isChecked
        etPort.isEnabled = !isRunning && !isScanning
        btnScan.visibility = if (rbClient.isChecked) View.VISIBLE else View.GONE
        btnScan.isEnabled = !isRunning && !isScanning && rbClient.isChecked
        btnScan.text = if (isScanning) "Scanning..." else "Scan"
        cbAutoReconnect.isEnabled = !isRunning && rbClient.isChecked
        btnSend.isEnabled = isRunning
        etSendMessage.isEnabled = isRunning

        btnStreamLogcat.visibility = if (rbServer.isChecked) View.VISIBLE else View.GONE
        btnStreamLogcat.isEnabled = isRunning
        btnStreamLogcat.text = if (isStreamingLogcat) "Stop Streaming Logcat" else "Stream Logcat"
    }

    private fun updateStatus() {
        // Always the device's CURRENT local IP (queried fresh, never cached) so whatever
        // is shown here is safe to type into another device's Client IP field right now.
        val currentIp = NetworkUtils.getLocalIpAddress(this) ?: "unknown"
        tvStatus.text = when {
            rbServer.isChecked && isRunning -> "Server listening on $currentIp:${etPort.text} | Clients: ${tcpServer.clientCount}"
            rbServer.isChecked -> "Stopped | Your IP: $currentIp"
            !isRunning -> "Stopped | Your IP: $currentIp"
            tcpClient.isConnected -> "Connected to ${etIp.text}:${etPort.text}"
            else -> "Connecting to ${etIp.text}:${etPort.text}..."
        }
    }

    private fun appendLog(line: String) {
        val time = timeFormat.format(Date())
        tvLog.append("[$time] $line\n")
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        logcatJob?.cancel()
        tcpServer.stop()
        tcpClient.stop()
    }
}
