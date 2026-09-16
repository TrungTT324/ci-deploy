package hdisoft.app.cideploy.features.bluetooth.presentation

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hdisoft.app.cideploy.R
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnector
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothConnectionState
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothMode
import hdisoft.app.cideploy.features.bluetooth.data.BluetoothMessageHistoryStore
import hdisoft.app.cideploy.features.bluetooth.security.data.BluetoothSecurityLayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random

@SuppressLint("MissingPermission")
class BluetoothChatActivity : AppCompatActivity() {

    private lateinit var tvChatStatus: TextView
    private lateinit var viewChatStatusDot: View
    private lateinit var tvChatLog: TextView
    private lateinit var scrollChat: ScrollView
    private lateinit var etChatMessage: EditText
    private lateinit var btnChatSend: ImageButton
    private lateinit var btnChatClear: ImageButton
    private lateinit var btnChatFile: ImageButton
    private lateinit var btnChatCamera: ImageButton
    private lateinit var btnChatGallery: ImageButton
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private lateinit var rvMessages: RecyclerView
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var historyStore: BluetoothMessageHistoryStore
    private lateinit var ivReceivedImage: android.widget.ImageView
    private var incomingFile: java.io.ByteArrayOutputStream? = null
    private var incomingFileName = ""
    private var cameraRequest = 4102
    private val cameraPermissionRequest = 4103

    private var securityLayer: BluetoothSecurityLayer? = null
    private var chatListener: BluetoothSecurityLayer.Listener? = null
    private var connector: BluetoothConnector? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var isConsoleMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_chat)

        // Setup Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbarChat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { 
            // Disconnect and exit when back navigation is clicked
            securityLayer?.disconnect()
            finish()
        }

        // Bind Views
        tvChatStatus = findViewById(R.id.tvChatStatus)
        viewChatStatusDot = findViewById(R.id.viewChatStatusDot)
        tvChatLog = findViewById(R.id.tvChatLog)
        scrollChat = findViewById(R.id.scrollChat)
        etChatMessage = findViewById(R.id.etChatMessage)
        btnChatSend = findViewById(R.id.btnChatSend)
        btnChatClear = findViewById(R.id.btnChatClear)
        btnChatFile = findViewById(R.id.btnChatFile)
        btnChatCamera = findViewById(R.id.btnChatCamera)
        btnChatGallery = findViewById(R.id.btnChatGallery)
        rvMessages = findViewById(R.id.rvMessages)
        messageAdapter = MessageAdapter(messages, fileExecutor)
        rvMessages.layoutManager = LinearLayoutManager(this)
        rvMessages.adapter = messageAdapter
        ivReceivedImage = findViewById(R.id.ivReceivedImage)

        // Get Shared Bluetooth Connector
        connector = BluetoothConnector.sharedConnector
        securityLayer = BluetoothSecurityLayer.sharedSecurityLayer
        if (connector == null || securityLayer == null ||
            connector?.currentState != BluetoothConnectionState.CONNECTED ||
            securityLayer?.isAuthenticated != true
        ) {
            Toast.makeText(this, "No active Bluetooth connection", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val role = if (connector?.currentMode == BluetoothMode.HOST) "Host" else "Client"
        val remoteDevice = connector?.connectedDevice
        val remoteName = remoteDevice?.name ?: remoteDevice?.address ?: "Unknown device"
        historyStore = BluetoothMessageHistoryStore(this, remoteDevice?.address ?: "unknown")
        historyStore.read().forEach { stored -> 
            if (stored.type == "text") {
                messages.add(ChatMessage.Text(stored.value, stored.outgoing))
            } else {
                java.io.File(stored.value).takeIf { it.exists() }?.let { 
                    messages.add(ChatMessage.Media(it, stored.outgoing)) 
                }
            } 
        }
        messageAdapter.notifyDataSetChanged()
        toolbar.title = "Bluetooth Chat · $role"

        // Setup callback
        chatListener = object : BluetoothSecurityLayer.Listener {
            override fun onStateChanged(state: BluetoothConnectionState) {
                runOnUiThread {
                    when (state) {
                        BluetoothConnectionState.DISCONNECTED, BluetoothConnectionState.IDLE -> {
                            updateChatStatus("Disconnected", Color.RED)
                            btnChatSend.isEnabled = false
                            etChatMessage.isEnabled = false
                            appendLog("[SYSTEM] Connection closed by remote device.")
                            Toast.makeText(this@BluetoothChatActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                            securityLayer?.listener = null
                            // Automatically exit chat activity after a short delay
                            tvChatLog.postDelayed({ finish() }, 300)
                        }
                        else -> {
                            updateChatStatus("State: $state", Color.GRAY)
                        }
                    }
                }
            }

            override fun onDeviceFound(device: BluetoothDevice) {}

            override fun onMessageReceived(message: String) {
                val isFile = handleFileMessage(message)
                if (!isFile) {
                    runOnUiThread { 
                        addMessage(ChatMessage.Text(message, false))
                        historyStore.add("text", message, false)
                        appendLog("RECV: $message") 
                    }
                    if (connector?.currentMode == BluetoothMode.HOST) sendHostAutoReply()
                }
            }

            override fun onMessageSent(message: String) {
                // Filter out file chunks to prevent freezing UI with thousands of log render tasks
                if (!message.startsWith("FILE|CHUNK|")) {
                    runOnUiThread { appendLog("SEND: $message") }
                }
            }

            override fun onAuthenticated(device: BluetoothDevice?) {
                runOnUiThread {
                    updateChatStatus("$role · Authenticated", Color.parseColor("#4CAF50"))
                }
            }

            override fun onAuthenticationRejected(reason: String) {
                runOnUiThread {
                    appendLog("[SECURITY] $reason")
                    Toast.makeText(this@BluetoothChatActivity, "Authentication rejected", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

            override fun onDiscoveryFinished() {}

            override fun onError(message: String) {
                runOnUiThread {
                    appendLog("[ERROR] $message")
                    Toast.makeText(this@BluetoothChatActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        chatListener?.let { securityLayer?.addListener(it) }

        // Listeners
        btnChatSend.setOnClickListener { sendMessage() }
        btnChatClear.setOnClickListener { tvChatLog.text = "" }
        btnChatFile.setOnClickListener { 
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { 
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE) 
            }
            startActivityForResult(intent, 4101) 
        }
        btnChatCamera.setOnClickListener { openCamera() }
        btnChatGallery.setOnClickListener { 
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { 
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                addCategory(Intent.CATEGORY_OPENABLE) 
            }
            startActivityForResult(intent, 4104) 
        }

        appendLog("[SYSTEM] $role connected to $remoteName. Chat terminal ready.")
        updateChatStatus("$role · Connected to $remoteName", Color.parseColor("#4CAF50"))
    }

    private fun sendMessage() {
        val msg = etChatMessage.text.toString().trim()
        if (msg.isEmpty()) return
        connector ?: return
        val currentSecurityLayer = securityLayer ?: return
        if (currentSecurityLayer.sendMessage(msg)) {
            addMessage(ChatMessage.Text(msg, true))
            historyStore.add("text", msg, true)
            etChatMessage.setText("")
        } else {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendHostAutoReply() {
        val replies = listOf("Đã nhận message.", "Host đã xử lý yêu cầu.", "CI-Deploy Host đang sẵn sàng.", "Message received successfully.")
        val reply = replies[Random.nextInt(replies.size)]
        if (securityLayer?.sendMessage(reply) == true) { 
            addMessage(ChatMessage.Text(reply, true))
            historyStore.add("text", reply, true)
            appendLog("AUTO REPLY: $reply") 
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4101 && resultCode == RESULT_OK) data?.data?.let { sendFile(it) }
        if (requestCode == 4104 && resultCode == RESULT_OK) data?.data?.let { sendFile(it) }
        if (requestCode == cameraRequest && resultCode == RESULT_OK) {
            (data?.extras?.get("data") as? Bitmap)?.let { sendBitmap(it) }
        }
    }

    private fun sendFile(uri: Uri) {
        fileExecutor.execute { sendFileInBackground(uri) }
    }

    private fun sendFileInBackground(uri: Uri) {
        val layer = securityLayer ?: return
        val name = if (uri.scheme == "file") {
            java.io.File(uri.path ?: "file").name 
        } else {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { 
                if (it.moveToFirst()) it.getString(0) else null 
            } ?: "file"
        }

        // Save bytes locally to app's cache directory using stream copy (prevents OOM on large files)
        val localFile = java.io.File(cacheDir, name)
        try {
            if (uri.scheme == "file") {
                val srcFile = java.io.File(uri.path ?: return)
                if (srcFile.absolutePath != localFile.absolutePath) {
                    srcFile.inputStream().use { input ->
                        localFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            } else {
                contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Failed to save file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val size = localFile.length()
        appendLog("FILE SEND: $name ($size bytes)")
        layer.sendMessage("FILE|START|${name.replace('|', '_')}|$size")

        // Stream file in 600-byte chunks without boxing list conversion (prevents GC overhead & UI freeze)
        try {
            val buffer = ByteArray(600)
            var bytesRead: Int
            var index = 0
            localFile.inputStream().use { inputStream ->
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val chunkToSend = if (bytesRead == 600) {
                        buffer
                    } else {
                        buffer.copyOf(bytesRead)
                    }
                    val base64Chunk = Base64.encodeToString(chunkToSend, Base64.NO_WRAP)
                    layer.sendMessage("FILE|CHUNK|$index|$base64Chunk")
                    index++
                }
            }
            layer.sendMessage("FILE|END")
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Failed sending file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        // Categorize file by its extension
        val ext = localFile.extension.lowercase()
        val isMedia = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "3gp", "mov", "avi")
        
        if (isMedia) {
            addMessage(ChatMessage.Media(localFile, true))
            historyStore.add("image", localFile.absolutePath, true)
        } else {
            runOnUiThread {
                addMessage(ChatMessage.Text("Sent file: $name", true))
                historyStore.add("text", "Sent file: $name", true)
            }
        }
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), cameraPermissionRequest)
            return
        }
        val intent = Intent("android.media.action.IMAGE_CAPTURE")
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No camera application available", Toast.LENGTH_LONG).show()
            appendLog("[ERROR] No camera activity available")
            return
        }
        try { 
            startActivityForResult(intent, cameraRequest) 
        } catch (e: Exception) {
            appendLog("[ERROR] Camera open failed: ${e.message}")
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else if (requestCode == cameraPermissionRequest) {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendBitmap(bitmap: Bitmap) {
        val file = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        sendFile(Uri.fromFile(file))
    }

    private fun handleFileMessage(message: String): Boolean {
        if (!message.startsWith("FILE|")) return false
        val p = message.split('|', limit = 4)
        when (p.getOrNull(1)) {
            "START" -> { 
                incomingFileName = p.getOrNull(2) ?: "file"
                incomingFile = java.io.ByteArrayOutputStream()
                appendLog("FILE RECEIVE: $incomingFileName") 
            }
            "CHUNK" -> p.getOrNull(3)?.let { encoded -> 
                try { 
                    incomingFile?.write(Base64.decode(encoded, Base64.NO_WRAP)) 
                } catch (e: IllegalArgumentException) { 
                    runOnUiThread { appendLog("[ERROR] Invalid image/file chunk") } 
                } 
            }
            "END" -> { 
                incomingFile?.let { 
                    val out = java.io.File(cacheDir, incomingFileName)
                    out.writeBytes(it.toByteArray())
                    appendLog("FILE SAVED: ${out.absolutePath}")
                    
                    val ext = out.extension.lowercase()
                    val isMedia = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "mkv", "3gp", "mov", "avi")
                    
                    if (isMedia) { 
                        addMessage(ChatMessage.Media(out, false))
                        historyStore.add("image", out.absolutePath, false)
                        
                        if (ext in listOf("jpg", "jpeg", "png", "webp")) {
                            runOnUiThread { 
                                ivReceivedImage.setImageBitmap(BitmapFactory.decodeFile(out.absolutePath))
                                ivReceivedImage.visibility = View.VISIBLE 
                            }
                        }
                    } else {
                        runOnUiThread {
                            addMessage(ChatMessage.Text("Received file: $incomingFileName", false))
                            historyStore.add("text", "Received file: $incomingFileName", false)
                        }
                    }
                }
                incomingFile = null 
            }
        }
        return true
    }

    private fun updateChatStatus(text: String, color: Int) {
        tvChatStatus.text = text
        viewChatStatusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun appendLog(line: String) {
        runOnUiThread {
            val time = timeFormat.format(Date())
            tvChatLog.append("[$time] $line\n")
            scrollChat.post { scrollChat.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun addMessage(message: ChatMessage) {
        runOnUiThread { 
            messages.add(message)
            messageAdapter.notifyItemInserted(messages.lastIndex)
            rvMessages.scrollToPosition(messages.lastIndex) 
        }
    }

    private sealed interface ChatMessage {
        val outgoing: Boolean
        val timestamp: Long
        data class Text(val value: String, override val outgoing: Boolean, override val timestamp: Long = System.currentTimeMillis()) : ChatMessage
        data class Media(val file: java.io.File, override val outgoing: Boolean, override val timestamp: Long = System.currentTimeMillis()) : ChatMessage
    }

    private class MessageAdapter(
        private val items: List<ChatMessage>,
        private val thumbnailExecutor: java.util.concurrent.ExecutorService
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        companion object {
            private const val TYPE_TEXT_INCOMING = 0
            private const val TYPE_TEXT_OUTGOING = 1
            private const val TYPE_IMAGE_INCOMING = 2
            private const val TYPE_IMAGE_OUTGOING = 3
        }

        override fun getItemViewType(position: Int): Int {
            return when (val item = items[position]) {
                is ChatMessage.Text -> if (item.outgoing) TYPE_TEXT_OUTGOING else TYPE_TEXT_INCOMING
                is ChatMessage.Media -> if (item.outgoing) TYPE_IMAGE_OUTGOING else TYPE_IMAGE_INCOMING
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = android.view.LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_TEXT_INCOMING -> {
                    val view = inflater.inflate(R.layout.item_chat_message_text_incoming, parent, false)
                    TextHolder(view)
                }
                TYPE_TEXT_OUTGOING -> {
                    val view = inflater.inflate(R.layout.item_chat_message_text_outgoing, parent, false)
                    TextHolder(view)
                }
                TYPE_IMAGE_INCOMING -> {
                    val view = inflater.inflate(R.layout.item_chat_message_image_incoming, parent, false)
                    MediaHolder(view)
                }
                TYPE_IMAGE_OUTGOING -> {
                    val view = inflater.inflate(R.layout.item_chat_message_image_outgoing, parent, false)
                    MediaHolder(view)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            when (item) {
                is ChatMessage.Text -> {
                    val textHolder = holder as TextHolder
                    textHolder.tvMessageValue.text = item.value
                    textHolder.tvMessageTime.text = timeStr
                }
                is ChatMessage.Media -> {
                    val mediaHolder = holder as MediaHolder
                    val isVideo = item.file.extension.lowercase() in listOf("mp4", "mkv", "3gp", "mov", "avi")
                    
                    if (isVideo) {
                        mediaHolder.ivMessageImage.setImageResource(android.R.drawable.ic_media_play)
                        mediaHolder.ivMessageImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                        
                        val handler = android.os.Handler(android.os.Looper.getMainLooper())
                        thumbnailExecutor.execute {
                            val thumbnail = try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    android.media.ThumbnailUtils.createVideoThumbnail(
                                        item.file, 
                                        android.util.Size(200, 200), 
                                        null
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    android.media.ThumbnailUtils.createVideoThumbnail(
                                        item.file.absolutePath, 
                                        android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                                    )
                                }
                            } catch (e: Exception) {
                                null
                            }
                            if (thumbnail != null) {
                                handler.post {
                                    mediaHolder.ivMessageImage.setImageBitmap(thumbnail)
                                    mediaHolder.ivMessageImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                }
                            }
                        }
                    } else {
                        mediaHolder.ivMessageImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        mediaHolder.ivMessageImage.setImageURI(Uri.fromFile(item.file))
                    }
                    
                    mediaHolder.tvMessageTime.text = timeStr
                    mediaHolder.itemView.setOnClickListener {
                        val context = it.context
                        try {
                            val authority = "${context.packageName}.fileprovider"
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, item.file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, if (isVideo) "video/*" else "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        class TextHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvMessageValue: TextView = itemView.findViewById(R.id.tvMessageValue)
            val tvMessageTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        }

        class MediaHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivMessageImage: android.widget.ImageView = itemView.findViewById(R.id.ivMessageImage)
            val tvMessageTime: TextView = itemView.findViewById(R.id.tvMessageTime)
        }
    }

    private fun toggleMode() {
        isConsoleMode = !isConsoleMode
        findViewById<View>(R.id.rvMessages).visibility = if (isConsoleMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.scrollChat).visibility = if (isConsoleMode) View.VISIBLE else View.GONE
        findViewById<View>(R.id.layoutChatInputBar).visibility = if (isConsoleMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.layoutConsoleLogBar).visibility = if (isConsoleMode) View.GONE else View.VISIBLE
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.add(0, 2, 0, if (isConsoleMode) "Show Chat" else "Show Console Log")?.apply {
            setIcon(if (isConsoleMode) R.drawable.ic_chat else R.drawable.ic_terminal)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu?.add(0, 1, 1, "Disconnect")?.apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> {
                securityLayer?.disconnect()
                finish()
                return true
            }
            2 -> {
                toggleMode()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        // Disconnect and exit when back button pressed
        securityLayer?.disconnect()
        super.onBackPressed()
    }

    override fun onDestroy() {
        fileExecutor.shutdownNow()
        super.onDestroy()
        chatListener?.let { securityLayer?.removeListener(it) }
        chatListener = null
    }
}
