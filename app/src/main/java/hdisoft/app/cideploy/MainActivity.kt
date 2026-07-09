package hdisoft.app.cideploy

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private val currentBuildNo = BuildConfig.BUILD_NO
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var downloadJob: Job? = null
    private lateinit var webView: WebView
    
    private val PREFS_NAME = "CI_Deploy_Prefs"
    private val KEY_HOST_IP = "host_ip"
    private var progressDialog: AlertDialog? = null
    private val urlPath = "ci-deploy"
    private var currentHost: String? = null
    private var isUpgradeDialogShowing = false
    private var isScanning = false
    private val FALLBACK_IPS = listOf("172.16.100.26", "10.0.2.2")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep the screen on while the app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Toolbar (Acts as Action Bar)
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize DrawerLayout & Toggle (if in portrait layout)
        val drawerLayout: DrawerLayout? = findViewById(R.id.drawerLayout)
        if (drawerLayout != null) {
            val toggle = ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
            )
            drawerLayout.addDrawerListener(toggle)
            toggle.syncState()
        }

        // Set up drawer menu click listeners
        val menuSettings: View = findViewById(R.id.menuSettings)
        val menuAbout: View = findViewById(R.id.menuAbout)
        val tvVersion: TextView = findViewById(R.id.tvVersion)

        tvVersion.text = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_NO})"

        menuSettings.setOnClickListener {
            drawerLayout?.closeDrawers()
            showSettingsDialog()
        }

        menuAbout.setOnClickListener {
            drawerLayout?.closeDrawers()
            showAboutDialog()
        }

        // Initialize WebView
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view?.loadUrl(url)
                    return true
                }
                return false
            }
            
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                Toast.makeText(this@MainActivity, "Load error: $description", Toast.LENGTH_LONG).show()
                handleLoadError(failingUrl)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val url = request?.url?.toString()
                val isMainFrame = request?.isForMainFrame ?: false
                if (isMainFrame && url != null) {
                    Toast.makeText(this@MainActivity, "Load error: ${error?.description}", Toast.LENGTH_LONG).show()
                    handleLoadError(url)
                }
            }
        }

        // Handle file downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file...")
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(applicationContext, "Downloading file...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to opening in external browser
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(applicationContext, "Cannot download: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Clean up any previously downloaded APKs to save space
        cleanupTempApkFiles()

        // Start host discovery flow or restore WebView state
        if (savedInstanceState != null) {
            currentHost = savedInstanceState.getString("current_host")
            webView.restoreState(savedInstanceState)
            currentHost?.let { host ->
                checkForUpdates(host)
            }
        } else {
            initHostDiscovery()
        }
    }

    private fun cleanupTempApkFiles() {
        try {
            val cacheFiles = cacheDir.listFiles()
            if (cacheFiles != null) {
                for (file in cacheFiles) {
                    if (file.isFile && file.name.endsWith(".apk")) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initHostDiscovery() {
        mainScope.launch {
            ensureWifiEnabled()
            
            // Wait up to 3 seconds for Wi-Fi to start turning on before checking saved host
            var wifiAttempts = 0
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            while (!wifiManager.isWifiEnabled && wifiAttempts < 3) {
                delay(1000)
                wifiAttempts++
            }

            val savedHost = getSavedHost()
            val currentSubnet = getLocalSubnet()
            
            var shouldCheckSavedHost = false
            if (savedHost != null) {
                if (currentSubnet != null) {
                    val lastDot = savedHost.lastIndexOf('.')
                    val savedHostSubnet = if (lastDot != -1) savedHost.substring(0, lastDot + 1) else null
                    if (savedHostSubnet == currentSubnet) {
                        shouldCheckSavedHost = true
                    }
                } else {
                    shouldCheckSavedHost = true
                }
            }

            if (shouldCheckSavedHost && savedHost != null) {
                showLoading("Checking saved host: $savedHost...")
                val works = withContext(Dispatchers.IO) {
                    checkHost(savedHost)
                }
                dismissLoading()
                if (works) {
                    loadHost(savedHost)
                    return@launch
                } else {
                    Toast.makeText(this@MainActivity, "Saved host unavailable. Scanning network...", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Scan LAN
            discoverAndLoadHost()
        }
    }

    private fun discoverAndLoadHost() {
        if (isScanning) return
        isScanning = true
        mainScope.launch {
            try {
                showLoading("Scanning local network (port 8080)...")
                ensureWifiEnabled()
                
                // If we just enabled it, let's wait a few seconds or poll up to 5 seconds for a Wi-Fi connection
                var subnet = getLocalSubnet()
                var attempts = 0
                while (subnet == null && attempts < 5) {
                    delay(1000)
                    subnet = getLocalSubnet()
                    attempts++
                }

                if (subnet == null) {
                    dismissLoading()
                    showNoHostDialog("Cannot determine local IP subnet. Please connect to a Wi-Fi network.")
                    return@launch
                }

                val discovered = scanLan(subnet)
                dismissLoading()

                if (discovered != null) {
                    saveHost(discovered)
                    loadHost(discovered)
                    Toast.makeText(this@MainActivity, "Connected to: $discovered", Toast.LENGTH_LONG).show()
                } else {
                    showNoHostDialog("No host found on port 8080 in the local network.")
                }
            } catch (e: Exception) {
                dismissLoading()
                showNoHostDialog("Scan error: ${e.message}")
            } finally {
                isScanning = false
            }
        }
    }

    private fun handleLoadError(failingUrl: String?) {
        val host = currentHost
        if (failingUrl != null && host != null && failingUrl.contains(host)) {
            if (!isScanning) {
                Toast.makeText(this@MainActivity, "Connection lost. Re-scanning network...", Toast.LENGTH_SHORT).show()
                discoverAndLoadHost()
            }
        }
    }

    private fun ensureWifiEnabled() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    wifiManager.isWifiEnabled = true
                    Toast.makeText(this, "Enabling Wi-Fi...", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(android.provider.Settings.Panel.ACTION_WIFI)
                    startActivity(intent)
                    Toast.makeText(this, "Please enable Wi-Fi", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadHost(host: String) {
        currentHost = host
        val finalUrl = "http://$host:8080/$urlPath"
        webView.loadUrl(finalUrl)
        checkForUpdates(host)
    }

    override fun onResume() {
        super.onResume()
        val host = currentHost
        if (host != null) {
            val currentSubnet = getLocalSubnet()
            val lastDot = host.lastIndexOf('.')
            val hostSubnet = if (lastDot != -1) host.substring(0, lastDot + 1) else null
            
            if (currentSubnet != null && hostSubnet != null && currentSubnet != hostSubnet) {
                currentHost = null
                discoverAndLoadHost()
            } else {
                checkForUpdates(host)
            }
        }
    }

    private fun showNoHostDialog(errorMessage: String) {
        AlertDialog.Builder(this)
            .setTitle("Host Not Found")
            .setMessage("$errorMessage\n\nMake sure the server is running on port 8080 and your phone is on the same local network.")
            .setCancelable(false)
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setPositiveButton("Retry") { _, _ ->
                discoverAndLoadHost()
            }
            .show()
    }

    private fun getSavedHost(): String? {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(KEY_HOST_IP, null)
    }

    private fun saveHost(ip: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(KEY_HOST_IP, ip).apply()
    }

    private fun showLoading(message: String) {
        dismissLoading()
        val padding = 50
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val textView = TextView(this).apply {
            text = message
            textSize = 16f
        }
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 30, 0, 0)
                gravity = android.view.Gravity.CENTER
            }
        }
        layout.addView(textView)
        layout.addView(progressBar)

        progressDialog = AlertDialog.Builder(this)
            .setView(layout)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun dismissLoading() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun getLocalSubnet(): String? {
        val localIp = getLocalIpAddress() ?: return null
        val lastDotIndex = localIp.lastIndexOf('.')
        if (lastDotIndex == -1) return null
        return localIp.substring(0, lastDotIndex + 1)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            
            // First pass: look for Wi-Fi or Ethernet interfaces
            for (networkInterface in interfaces) {
                val name = networkInterface.name.lowercase()
                if (name.contains("wlan") || name.contains("eth")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: continue
                            if (ip.indexOf(':') < 0) { // IPv4 check
                                if (ip.startsWith("192.168.") || ip.startsWith("172.16.") || 
                                    ip.startsWith("172.31.") || ip.startsWith("10.")) {
                                    return ip
                                }
                            }
                        }
                    }
                }
            }
            
            // Second pass: look at all other interfaces (fallback)
            for (networkInterface in interfaces) {
                val name = networkInterface.name.lowercase()
                if (!name.contains("wlan") && !name.contains("eth")) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val ip = address.hostAddress ?: continue
                            if (ip.indexOf(':') < 0) { // IPv4 check
                                if (ip.startsWith("192.168.") || ip.startsWith("172.16.") || 
                                    ip.startsWith("172.31.") || ip.startsWith("10.")) {
                                    return ip
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    private suspend fun scanLan(subnet: String): String? = coroutineScope {
        val channel = Channel<String>(Channel.UNLIMITED)
        val semaphore = Semaphore(40) // limit to 40 concurrent scans to prevent socket congestion
        
        // Combine subnet IPs and fallback IPs
        val ipsToScan = (1..254).map { "$subnet$it" }.toMutableList()
        for (fallback in FALLBACK_IPS) {
            if (!ipsToScan.contains(fallback)) {
                ipsToScan.add(fallback)
            }
        }
        
        val jobs = ipsToScan.map { ip ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    if (checkHost(ip)) {
                        channel.trySend(ip)
                    }
                }
            }
        }

        val tracker = launch {
            jobs.forEach { it.join() }
            channel.close()
        }

        var foundIp: String? = null
        try {
            for (ip in channel) {
                foundIp = ip
                break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            jobs.forEach { it.cancel() }
            tracker.cancel()
        }

        return@coroutineScope foundIp
    }

    private fun checkHost(ip: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("http://$ip:8080/$urlPath/ci-deploy-version.json")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000 // 2 seconds timeout
            connection.readTimeout = 2000
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val stream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                val buffer = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    buffer.append(line)
                }
                val response = buffer.toString()
                if (response.contains("CI-Deploy")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // ignore connection errors
        } finally {
            connection?.disconnect()
        }
        return false
    }

    private fun checkForUpdates(host: String) {
        mainScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    var connection: HttpURLConnection? = null
                    var reader: BufferedReader? = null
                    try {
                        val url = URL("http://$host:8080/$urlPath/ci-deploy-version.json")
                        connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.connect()

                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val stream = connection.inputStream
                            reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                            val buffer = StringBuilder()
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                buffer.append(line).append("\n")
                            }
                            return@withContext buffer.toString()
                        }
                        return@withContext null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@withContext null
                    } finally {
                        reader?.close()
                        connection?.disconnect()
                    }
                }

                if (jsonString != null) {
                    val jsonObject = JSONObject(jsonString)
                    val remoteBuildNo = jsonObject.optLong("buildNo", 0)
                    val remoteVersion = jsonObject.optString("version", "1.0.0")
                    val buildNote = jsonObject.optString("buildNote", "")
                    val downloadUrl = jsonObject.optString("url", "")

                    if (remoteBuildNo > currentBuildNo) {
                        showUpgradeDialog(remoteVersion, buildNote, downloadUrl, host)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpgradeDialog(version: String, note: String, downloadUrl: String, host: String) {
        if (isUpgradeDialogShowing) return
        isUpgradeDialogShowing = true

        // Dynamically format download URL to match discovered host and urlPath
        var finalDownloadUrl = downloadUrl
        try {
            val uri = Uri.parse(downloadUrl)
            val fileName = uri.lastPathSegment ?: "CI-Deploy_debug.apk"
            finalDownloadUrl = "http://$host:8080/$urlPath/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
        }

        AlertDialog.Builder(this)
            .setTitle("New Upgrade")
            .setMessage("Version: $version\n\nWhat's new:\n$note")
            .setCancelable(false)
            .setNegativeButton("No") { dialog, _ ->
                isUpgradeDialogShowing = false
                dialog.dismiss()
            }
            .setPositiveButton("Yes") { dialog, _ ->
                isUpgradeDialogShowing = false
                dialog.dismiss()
                startDownload(finalDownloadUrl)
            }
            .show()
    }

    private fun startDownload(urlString: String) {
        val padding = 50
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val statusText = TextView(this).apply {
            text = "Initializing download..."
            textSize = 14f
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 30, 0, 0)
            }
        }
        layout.addView(statusText)
        layout.addView(progressBar)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Install", null)
            .create()

        dialog.show()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        positiveButton.visibility = View.GONE

        val apkFile = File(cacheDir, "CI-Deploy_upgrade.apk")

        negativeButton.setOnClickListener {
            downloadJob?.cancel()
            dialog.dismiss()
        }

        downloadJob = mainScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    var connection: HttpURLConnection? = null
                    var inputStream: InputStream? = null
                    var outputStream: FileOutputStream? = null
                    try {
                        val url = URL(urlString)
                        connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 10000
                        connection.readTimeout = 15000
                        connection.connect()

                        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                            return@withContext "Server returned HTTP ${connection.responseCode}"
                        }

                        val fileLength = connection.contentLength
                        inputStream = connection.inputStream
                        outputStream = FileOutputStream(apkFile)

                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int

                        while (inputStream.read(data).also { count = it } != -1) {
                            if (!isActive) {
                                return@withContext "Cancelled"
                            }
                            total += count
                            outputStream.write(data, 0, count)

                            if (fileLength > 0) {
                                val progressPercent = (total * 100 / fileLength).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = progressPercent
                                    val totalMB = String.format("%.1f", total.toDouble() / (1024 * 1024))
                                    val lengthMB = String.format("%.1f", fileLength.toDouble() / (1024 * 1024))
                                    statusText.text = "Downloading: $totalMB MB / $lengthMB MB ($progressPercent%)"
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    progressBar.isIndeterminate = true
                                    val totalMB = String.format("%.1f", total.toDouble() / (1024 * 1024))
                                    statusText.text = "Downloading: $totalMB MB"
                                }
                            }
                        }
                        return@withContext "Success"
                    } catch (e: Exception) {
                        return@withContext e.message ?: "Unknown error"
                    } finally {
                        outputStream?.close()
                        inputStream?.close()
                        connection?.disconnect()
                    }
                }

                if (result == "Success") {
                    statusText.text = "Download completed! Press Install to continue."
                    progressBar.progress = 100
                    progressBar.isIndeterminate = false

                    positiveButton.visibility = View.VISIBLE
                    positiveButton.setOnClickListener {
                        installApk(apkFile)
                    }

                    negativeButton.text = "Later"
                    negativeButton.setOnClickListener {
                        dialog.dismiss()
                    }
                } else if (result == "Cancelled") {
                    statusText.text = "Download cancelled."
                } else {
                    statusText.text = "Error: $result"
                    negativeButton.text = "Close"
                    negativeButton.setOnClickListener {
                        dialog.dismiss()
                    }
                }
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                negativeButton.text = "Close"
                negativeButton.setOnClickListener {
                    dialog.dismiss()
                }
            }
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) return

        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val authority = "$packageName.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start install: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettingsDialog() {
        val input = android.widget.EditText(this).apply {
            setText(currentHost ?: "")
            hint = "192.168.1.135"
        }
        AlertDialog.Builder(this)
            .setTitle("Configure Server Host")
            .setMessage("Enter the IP address of the CI server:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newHost = input.text.toString().trim()
                if (newHost.isNotEmpty()) {
                    saveHost(newHost)
                    loadHost(newHost)
                    Toast.makeText(this, "Host updated: $newHost", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About CI-Deploy")
            .setMessage("Version: ${BuildConfig.VERSION_NAME}\nBuild No: ${BuildConfig.BUILD_NO}\n\nDeveloped by xSofts.")
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onBackPressed() {
        val drawerLayout: DrawerLayout? = findViewById(R.id.drawerLayout)
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
        outState.putString("current_host", currentHost)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
