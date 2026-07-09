package hdisoft.app.cideploy

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.URLUtil
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
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

class MainActivity : Activity() {

    private val currentBuildNo = BuildConfig.BUILD_NO
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var downloadJob: Job? = null
    private lateinit var webView: WebView
    
    private val PREFS_NAME = "CI_Deploy_Prefs"
    private val KEY_HOST_IP = "host_ip"
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize WebView
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        webView.webViewClient = WebViewClient()

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

        // Start host discovery flow
        initHostDiscovery()
    }

    private fun initHostDiscovery() {
        mainScope.launch {
            val savedHost = getSavedHost()
            if (savedHost != null) {
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
        mainScope.launch {
            showLoading("Scanning local network (port 8080)...")
            val subnet = getLocalSubnet()
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
        }
    }

    private fun loadHost(host: String) {
        webView.loadUrl("http://$host:8080")
        checkForUpdates(host)
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
            for (networkInterface in interfaces) {
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
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    private suspend fun scanLan(subnet: String): String? = coroutineScope {
        val channel = Channel<String>(Channel.UNLIMITED)
        val semaphore = Semaphore(40) // limit to 40 concurrent scans to prevent socket congestion
        
        val jobs = (1..254).map { i ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    val ip = "$subnet$i"
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
            val url = URL("http://$ip:8080/apps/ci-deploy/ci-deploy-version.json")
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
                        val url = URL("http://$host:8080/apps/ci-deploy/ci-deploy-version.json")
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
        // Dynamically replace host in download URL to match discovered host IP
        var finalDownloadUrl = downloadUrl
        try {
            val originalUri = Uri.parse(downloadUrl)
            if (originalUri.host != null) {
                finalDownloadUrl = downloadUrl.replace(originalUri.host!!, host)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        AlertDialog.Builder(this)
            .setTitle("New Upgrade")
            .setMessage("Version: $version\n\nWhat's new:\n$note")
            .setCancelable(false)
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
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

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
