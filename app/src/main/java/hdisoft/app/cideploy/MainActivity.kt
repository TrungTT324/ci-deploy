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
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val currentBuildNo = BuildConfig.BUILD_NO
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var downloadJob: Job? = null
    private lateinit var webView: WebView

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

        webView.loadUrl("http://172.16.100.26:8080")

        // Check for updates
        checkForUpdates()
    }

    private fun checkForUpdates() {
        mainScope.launch {
            try {
                val jsonString = withContext(Dispatchers.IO) {
                    var connection: HttpURLConnection? = null
                    var reader: BufferedReader? = null
                    try {
                        val url = URL("http://172.16.100.26:8080/apps/ci-deploy/ci-deploy-version.json")
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
                        showUpgradeDialog(remoteVersion, buildNote, downloadUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpgradeDialog(version: String, note: String, downloadUrl: String) {
        AlertDialog.Builder(this)
            .setTitle("New Upgrade")
            .setMessage("Version: $version\n\nWhat's new:\n$note")
            .setCancelable(false)
            .setNegativeButton("No", null)
            .setPositiveButton("Yes") { _, _ ->
                startDownload(downloadUrl)
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
