package hdisoft.app.cideploy.features.main.presentation

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowManager
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import hdisoft.app.appupdate.AppUpdateDownloadActions
import hdisoft.app.appupdate.AppUpdateDownloadFlow
import hdisoft.app.appupdate.AppUpdateSettings
import hdisoft.app.appupdate.domain.model.UpdateInfo
import hdisoft.app.appupdate.hostdiscovery.presentation.HostDiscoveryService
import hdisoft.app.cideploy.BuildConfig
import hdisoft.app.cideploy.R
import hdisoft.app.core.utils.DeviceUtils
import hdisoft.app.logcat.presentation.LogStreamService
import hdisoft.app.logcat.presentation.LogcatActivity
import hdisoft.app.webserver.HttpWebServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val currentBuildNo = BuildConfig.BUILD_NO
    private lateinit var webView: WebView
    internal val viewModel: MainViewModel by viewModels()
    
    internal val urlPath = "ci-deploy"
    private var progressDialog: AlertDialog? = null

    internal val updateDownloadActions = object : AppUpdateDownloadActions {
        override fun startDownload(info: UpdateInfo, url: String, targetFile: File) = viewModel.startDownload(info, url, targetFile)
        override fun cancelDownload() = viewModel.cancelDownload()
        override fun verifyDownloadedApk(file: File, autoInstallWithRoot: Boolean) = viewModel.verifyDownloadedApk(applicationContext, file, autoInstallWithRoot)
        override fun installApk(file: File) { viewModel.installApk(this@MainActivity, file) }
        override fun resetUpdateOperation() = viewModel.resetUpdateOperation()
        override fun dismissVersion(buildNo: Long) = viewModel.dismissVersion(buildNo)
        override fun clearUpdateInfo() = viewModel.clearUpdateInfo()
        override fun resumePeriodicUpdateCheck() = viewModel.resumePeriodicUpdateCheck()
    }
    internal val updateDownloadFlow: AppUpdateDownloadFlow by lazy {
        AppUpdateDownloadFlow(this, updateDownloadActions, apkFileName = "CI-Deploy_upgrade.apk")
    }

    private var panelHostNotFound: View? = null
    private var tvPanelError: TextView? = null
    private var btnPanelRetry: View? = null
    private var pbPanelProgress: ProgressBar? = null

    internal var discoveryService: HostDiscoveryService? = null
    private var isDiscoveryBound = false

    private val discoveryConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as HostDiscoveryService.LocalBinder
            val s = binder.getService()
            discoveryService = s
            isDiscoveryBound = true
            setupDiscoveryCallback(s)
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            discoveryService = null
            isDiscoveryBound = false
        }
    }

    internal var webServerService: HttpWebServerService? = null
    internal var isWebServerBound = false

    internal val webServerConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as HttpWebServerService.LocalBinder
            val s = binder.getService()
            webServerService = s
            isWebServerBound = true
            updateWebConsoleInfo()
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            webServerService = null
            isWebServerBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Keep the screen on while the app is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        updateRootStatusTitle()
        supportActionBar?.subtitle = "Build: ${formatBuildNo(currentBuildNo)}"

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
        val menuLogcat: View = findViewById(R.id.menuLogcat)
        val menuApiExplorer: View = findViewById(R.id.menuApiExplorer)
        val menuWebserver: View = findViewById(R.id.menuWebserver)
        val menuTestTcp: View = findViewById(R.id.menuTestTcp)
        val menuBluetoothTest: View = findViewById(R.id.menuBluetoothTest)
        val menuAbout: View = findViewById(R.id.menuAbout)
        val tvVersion: TextView = findViewById(R.id.tvVersion)

        tvVersion.text = "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_NO})"

        menuSettings.setOnClickListener {
            drawerLayout?.closeDrawers()
            showSettingsDialog()
        }

        menuLogcat.setOnClickListener {
            drawerLayout?.closeDrawers()
            startActivity(Intent(this, LogcatActivity::class.java))
        }

        menuApiExplorer.setOnClickListener {
            drawerLayout?.closeDrawers()
            startActivity(Intent(this, hdisoft.app.cideploy.features.apiexplorer.presentation.ApiExplorerActivity::class.java))
        }

        menuWebserver.setOnClickListener {
            drawerLayout?.closeDrawers()
            openWebserverSettings()
        }

        menuTestTcp.setOnClickListener {
            drawerLayout?.closeDrawers()
            startActivity(Intent(this, hdisoft.app.cideploy.features.testtcp.presentation.TestTcpActivity::class.java))
        }

        menuBluetoothTest.setOnClickListener {
            drawerLayout?.closeDrawers()
            openBluetoothTest()
        }

        menuAbout.setOnClickListener {
            drawerLayout?.closeDrawers()
            showAboutDialog()
        }

        val switchWebserver: androidx.appcompat.widget.SwitchCompat = findViewById(R.id.switchWebserver)
        switchWebserver.setOnCheckedChangeListener { _, isChecked ->
            val isCurrentlyRunning = hdisoft.app.webserver.HttpWebServerService.isRunning
            if (isChecked && !isCurrentlyRunning) {
                startAndBindWebServer()
            } else if (!isChecked && isCurrentlyRunning) {
                unbindWebServerIfBound()
                stopService(Intent(this, hdisoft.app.webserver.HttpWebServerService::class.java))
                updateWebConsoleInfo()
            }
        }

        // Initialize Host Not Found Panel
        panelHostNotFound = findViewById(R.id.panelHostNotFound)
        tvPanelError = findViewById(R.id.tvPanelError)
        btnPanelRetry = findViewById(R.id.btnPanelRetry)
        pbPanelProgress = findViewById(R.id.pbPanelProgress)
        btnPanelRetry?.setOnClickListener {
            viewModel.clearScanError()
            discoveryService?.startDiscovery()
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

        webView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    android.util.Log.d("CI_DEPLOY_WEB", "[Console] ${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                android.util.Log.d("CI_DEPLOY_WEB", "shouldOverrideUrlLoading: $url")
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                    view?.loadUrl(url)
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                android.util.Log.d("CI_DEPLOY_WEB", "onPageStarted: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("CI_DEPLOY_WEB", "onPageFinished: $url")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                android.util.Log.e("CI_DEPLOY_WEB", "onReceivedHttpError: url=${request?.url}, code=${errorResponse?.statusCode}, phrase=${errorResponse?.reasonPhrase}")
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: android.webkit.SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                super.onReceivedSslError(view, handler, error)
                android.util.Log.e("CI_DEPLOY_WEB", "onReceivedSslError: $error")
            }
            
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                android.util.Log.e("CI_DEPLOY_WEB", "onReceivedError (legacy): code=$errorCode, desc=$description, url=$failingUrl")
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
                android.util.Log.e("CI_DEPLOY_WEB", "onReceivedError: url=$url, isMainFrame=$isMainFrame, code=${error?.errorCode}, desc=${error?.description}")
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
        viewModel.cleanupTempApkFiles(cacheDir)

        viewModel.performSilentAuth(this)

        // Start Logcat stream service on app launch
        try {
            val logcatIntent = Intent(this, LogStreamService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(logcatIntent)
            } else {
                startService(logcatIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Set check interval
        val savedInterval = AppUpdateSettings.getCheckIntervalSeconds(this)
        viewModel.setCheckInterval(savedInterval)

        // Observe ViewModel States
        observeViewModel()

        // Start and bind HostDiscoveryService
        val discoveryIntent = Intent(this, HostDiscoveryService::class.java)
        startService(discoveryIntent)
        bindService(discoveryIntent, discoveryConnection, BIND_AUTO_CREATE)

        // Start and bind HttpWebServerService
        startAndBindWebServer()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
            val restoredHost = savedInstanceState.getString("current_host")
            if (restoredHost != null) {
                viewModel.saveManualHost(this, restoredHost)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isDiscoveryBound) {
            unbindService(discoveryConnection)
            isDiscoveryBound = false
        }
        unbindWebServerIfBound()
    }

    private fun setupDiscoveryCallback(service: HostDiscoveryService) {
        android.util.Log.d("CI_DEPLOY_ROOT", "setupDiscoveryCallback called. Current service state: ${service.getServiceState()}")
        service.onStateChangedListener = { state ->
            android.util.Log.d("CI_DEPLOY_ROOT", "onStateChangedListener state changed: $state")
            runOnUiThread {
                handleDiscoveryState(state)
            }
        }
        handleDiscoveryState(service.getServiceState())

        // First bind (e.g. cold start): kick off the check-version flow right
        // away. onResume() re-triggers this on every subsequent foregrounding;
        // startDiscovery() itself is a no-op while already in Progress, so
        // calling it here too just covers the race where bind completes after
        // onResume already ran and found no service yet.
        service.startDiscovery()
    }

    private fun handleDiscoveryState(state: HostDiscoveryService.State) {
        android.util.Log.d("CI_DEPLOY_ROOT", "handleDiscoveryState received state: $state")
        when (state) {
            is HostDiscoveryService.State.Idle -> {
                viewModel.setIsScanning(false)
                viewModel.setLoadingMessage(null)
                val currentHost = viewModel.currentHost.value
                if (currentHost != null) {
                    panelHostNotFound?.visibility = View.VISIBLE
                    panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                    tvPanelError?.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                    tvPanelError?.text = "Connected to http://$currentHost:8080/$urlPath/"
                    pbPanelProgress?.visibility = View.GONE
                    btnPanelRetry?.visibility = View.GONE
                } else {
                    panelHostNotFound?.visibility = View.VISIBLE
                    panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F7"))
                    tvPanelError?.setTextColor(android.graphics.Color.parseColor("#757575"))
                    tvPanelError?.text = "Not connected to any host."
                    pbPanelProgress?.visibility = View.GONE
                    btnPanelRetry?.visibility = View.VISIBLE
                }
            }
            is HostDiscoveryService.State.Progress -> {
                viewModel.setIsScanning(true)
                viewModel.setLoadingMessage(null)
                viewModel.clearScanError()
                
                panelHostNotFound?.visibility = View.VISIBLE
                panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#E8F0FE"))
                tvPanelError?.setTextColor(android.graphics.Color.parseColor("#1A73E8"))
                tvPanelError?.text = state.message
                pbPanelProgress?.visibility = View.VISIBLE
                btnPanelRetry?.visibility = View.GONE
            }
            is HostDiscoveryService.State.Success -> {
                viewModel.setIsScanning(false)
                viewModel.setLoadingMessage(null)
                viewModel.setHost(state.host)
                viewModel.clearScanError()
                
                panelHostNotFound?.visibility = View.VISIBLE
                panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                tvPanelError?.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                tvPanelError?.text = "Connected to http://${state.host}:8080/$urlPath/"
                pbPanelProgress?.visibility = View.GONE
                btnPanelRetry?.visibility = View.GONE
            }
            is HostDiscoveryService.State.Error -> {
                viewModel.setIsScanning(false)
                viewModel.setLoadingMessage(null)
                viewModel.setScanError(state.error)
                
                panelHostNotFound?.visibility = View.VISIBLE
                panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#FDEDEC"))
                tvPanelError?.setTextColor(android.graphics.Color.parseColor("#C0392B"))
                tvPanelError?.text = state.error
                pbPanelProgress?.visibility = View.GONE
                btnPanelRetry?.visibility = View.VISIBLE
            }
        }
    }

    private fun observeViewModel() {
        viewModel.currentHost.observe(this) { host ->
            android.util.Log.d("CI_DEPLOY_ROOT", "viewModel.currentHost observer triggered with host: $host")
            if (host != null) {
                val finalUrl = "http://$host:8080/$urlPath"
                android.util.Log.d("CI_DEPLOY_ROOT", "Loading finalUrl: $finalUrl")
                webView.loadUrl(finalUrl)

                panelHostNotFound?.visibility = View.VISIBLE
                panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                tvPanelError?.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                tvPanelError?.text = "Connected to http://$host:8080/$urlPath/"
                pbPanelProgress?.visibility = View.GONE
                btnPanelRetry?.visibility = View.GONE
            } else {
                panelHostNotFound?.visibility = View.VISIBLE
                panelHostNotFound?.setBackgroundColor(android.graphics.Color.parseColor("#F5F5F7"))
                tvPanelError?.setTextColor(android.graphics.Color.parseColor("#757575"))
                tvPanelError?.text = "Not connected to any host."
                pbPanelProgress?.visibility = View.GONE
                btnPanelRetry?.visibility = View.VISIBLE
            }
        }

        viewModel.loadingMessage.observe(this) { msg ->
            if (msg != null) {
                showLoading(msg)
            } else {
                dismissLoading()
            }
        }

        viewModel.scanError.observe(this) { err ->
            // Handled directly inside handleDiscoveryState
        }

        // App update extension observers
        setupAppUpdateObservers()

        val tvUserStatus: TextView = findViewById(R.id.tvUserStatus)
        viewModel.currentUser.observe(this) { user ->
            if (user != null) {
                tvUserStatus.text = "Welcome, ${user.username}"
            } else {
                tvUserStatus.text = "Guest Mode"
            }
        }

        viewModel.isAuthenticating.observe(this) { authenticating ->
            if (authenticating) {
                tvUserStatus.text = "Authenticating..."
            }
        }
    }

    private fun handleLoadError(failingUrl: String?) {
        val host = viewModel.currentHost.value
        if (failingUrl != null && host != null && failingUrl.contains(host)) {
            if (viewModel.isScanning.value == false) {
                Toast.makeText(this@MainActivity, "Connection lost. Re-scanning network...", Toast.LENGTH_SHORT).show()
                discoveryService?.startDiscovery()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateRootStatusTitle()
        updateWebConsoleInfo()
        viewModel.startPeriodicUpdateCheck()

        // Single entry point for the "check version" flow: HostDiscoveryService
        // itself tries the last known host first, then (only if that fails and
        // the source mode is LAN_ONLY) re-scans. On success it reports back via
        // handleDiscoveryState -> viewModel.setHost(...), which is what actually
        // (re)loads the WebView and triggers viewModel.checkForUpdates(host).
        discoveryService?.startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopPeriodicUpdateCheck()
    }

    private fun showLoading(message: String) {
        dismissLoading()
        val dp = resources.displayMetrics.density
        val padding = (24 * dp).toInt()
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
                setMargins(0, (16 * dp).toInt(), 0, 0)
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

    private fun showSettingsDialog() {
        val context = this
        val dp = context.resources.displayMetrics.density

        val container = android.widget.ScrollView(context).apply {
            val horizontalPadding = (24 * dp).toInt()
            val verticalPadding = (16 * dp).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }

        // 1. CI Server Host
        val tvHostHeader = android.widget.TextView(context).apply {
            text = "CI Server Host"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.BLACK)
        }

        val etHost = android.widget.EditText(context).apply {
            setText(viewModel.currentHost.value ?: "")
            hint = "192.168.1.135"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        layout.addView(tvHostHeader)
        layout.addView(etHost)

        // Separator
        val separator = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).apply {
                setMargins(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            }
            setBackgroundColor(android.graphics.Color.LTGRAY)
        }
        layout.addView(separator)

        // 2. Web Console Config (:libs:webserver — see MainActivityWebServer.kt)
        val saveWebConsoleSettings = addWebConsoleSettingsSection(layout, dp)

        container.addView(layout)

        AlertDialog.Builder(context)
            .setTitle("Settings")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newHost = etHost.text.toString().trim()
                if (newHost.isNotEmpty()) {
                    viewModel.saveManualHost(context, newHost)
                }

                saveWebConsoleSettings()

                Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()
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

    /**
     * Re-checks root status on every call rather than caching it once —
     * DeviceUtils.isDeviceRooted() itself only caches a positive result, so an
     * early false (root daemon not warmed up yet, root manager grant prompt
     * not yet answered) won't get stuck; calling this again on each resume
     * lets the "(rooted)" badge catch up once root actually becomes usable.
     * Runs the (blocking, up-to-5s) `su` check off the main thread since it's
     * now invoked on every resume, not just once at startup.
     */
    private fun updateRootStatusTitle() {
        supportActionBar?.title = "CI-Deploy"
        lifecycleScope.launch {
            val rooted = withContext(Dispatchers.IO) { DeviceUtils.isDeviceRooted() }
            supportActionBar?.title = if (rooted) "CI-Deploy (rooted)" else "CI-Deploy"
        }
    }

    private fun formatBuildNo(buildNo: Long): String {
        val buildNoStr = buildNo.toString()
        if (buildNoStr.length == 12) {
            val day = buildNoStr.substring(6, 8)
            val hour = buildNoStr.substring(8, 10)
            val minute = buildNoStr.substring(10, 12)
            return "$day @ $hour:$minute"
        }
        return buildNoStr
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
        outState.putString("current_host", viewModel.currentHost.value)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_info) {
            showAppUpdateInfoDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}
