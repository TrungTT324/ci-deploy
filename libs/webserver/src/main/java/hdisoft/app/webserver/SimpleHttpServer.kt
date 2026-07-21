package hdisoft.app.webserver

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class SimpleHttpServer(
    private val context: Context,
    private val port: Int,
    private val webConfig: WebConfig
) {
    interface ScriptExecutor {
        fun executeScript(scriptJson: String): String
    }

    companion object {
        var scriptExecutor: ScriptExecutor? = null
    }

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverJob: Job? = null
    private var isRunning = false
    private val dbHelper = DatabaseHelper(context)

    fun start() {
        if (isRunning) return
        isRunning = true
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isActive && isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(socket)
                    }
                }
            } catch (e: Exception) {
                WebserverLogger.log("Server error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        var reader: BufferedReader? = null
        var output: OutputStream? = null
        val clientIp = socket.inetAddress.hostAddress ?: "unknown"
        val clientPort = socket.port
        
        try {
            reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = socket.getOutputStream()

            // 1. Read Request Line
            val headerLine = reader.readLine() ?: return
            val parts = headerLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val pathWithParams = parts[1]
            val path = pathWithParams.substringBefore("?")

            // Log incoming request
            WebserverLogger.log("$method $path from $clientIp:$clientPort")

            // 2. Read HTTP Headers to find Content-Length
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) {
                    break // Empty line signals the end of headers
                }
                if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line!!.substring("Content-Length:".length).trim().toIntOrNull() ?: 0
                }
            }

            // 3. Read HTTP Body if Content-Length > 0
            var body = ""
            if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(bodyChars, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                body = String(bodyChars)
            }

            // 4. Parse query parameters
            val params = pathWithParams.substringAfter("?", "")
            val queryMap = mutableMapOf<String, String>()
            if (params.isNotEmpty()) {
                params.split("&").forEach { param ->
                    val pair = param.split("=")
                    if (pair.size == 2) {
                        queryMap[pair[0]] = pair[1]
                    }
                }
            }
            val idParam = queryMap["id"]?.toLongOrNull()

            // 5. Handle CORS Preflight request
            if (method == "OPTIONS") {
                val headers = "HTTP/1.1 204 No Content\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                        "Access-Control-Allow-Headers: Content-Type\r\n" +
                        "Connection: close\r\n\r\n"
                output.write(headers.toByteArray())
                return
            }

            // 6. Router logic
            when (method) {
                "GET" -> {
                    when {
                        path == "/" || path == "/index.html" -> {
                            val indexFile = if (assetExists("app_index.html")) "app_index.html" else "index.html"
                            serveAssetFile(output, indexFile, "text/html")
                        }

                         path.startsWith("/css/") -> {
                            val assetPath = path.substring(1)
                            serveAssetFile(output, assetPath, "text/css")
                        }
                        path.startsWith("/js/") -> {
                            val assetPath = path.substring(1)
                            serveAssetFile(output, assetPath, "application/javascript")
                        }
                        path.startsWith("/img/") -> {
                            val assetPath = path.substring(1)
                            val contentType = when {
                                path.endsWith(".png") -> "image/png"
                                path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
                                path.endsWith(".gif") -> "image/gif"
                                path.endsWith(".svg") -> "image/svg+xml"
                                else -> "application/octet-stream"
                            }
                            serveAssetFile(output, assetPath, contentType)
                        }
                        path.startsWith("/fonts/") -> {
                            val assetPath = path.substring(1)
                            val contentType = when {
                                path.endsWith(".ttf") -> "font/ttf"
                                path.endsWith(".woff") -> "font/woff"
                                path.endsWith(".woff2") -> "font/woff2"
                                path.endsWith(".otf") -> "font/otf"
                                else -> "application/octet-stream"
                            }
                            serveAssetFile(output, assetPath, contentType)
                        }
                        path == "/api/config" -> {
                            val jsonResponse = "{\"requireAuthen\":${webConfig.requireAuthen}}"
                            sendResponse(output, "HTTP/1.1 200 OK", "application/json", jsonResponse.toByteArray())
                        }

                        path == "/api/auth" -> {
                            val correctPin = webConfig.authPin
                            val pinParam = queryMap["pin"]

                            if (!webConfig.requireAuthen) {
                                WebserverLogger.log("Authentication BYPASSED for $clientIp")
                                val jsonResponse = "{\"status\":\"success\",\"token\":\"bypass_token\"}"
                                sendResponse(output, "HTTP/1.1 200 OK", "application/json", jsonResponse.toByteArray())
                            } else if (pinParam == correctPin) {
                                WebserverLogger.log("Authentication SUCCESS for $clientIp")
                                val jsonResponse = "{\"status\":\"success\",\"token\":\"mock_token_$correctPin\"}"
                                sendResponse(output, "HTTP/1.1 200 OK", "application/json", jsonResponse.toByteArray())
                            } else {
                                WebserverLogger.log("Authentication FAILED (PIN: $pinParam) for $clientIp")
                                val jsonResponse = "{\"status\":\"error\",\"message\":\"Invalid PIN\"}"
                                sendResponse(output, "HTTP/1.1 401 Unauthorized", "application/json", jsonResponse.toByteArray())
                            }
                        }
                        path == "/api/records" -> {
                            if (idParam != null) {
                                val record = dbHelper.getRecordById(idParam)
                                if (record != null) {
                                    sendResponse(output, "HTTP/1.1 200 OK", "application/json", record.toString().toByteArray())
                                } else {
                                    val errorJson = "{\"status\":\"error\",\"message\":\"Record not found\"}"
                                    sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                                }
                            } else {
                                val records = dbHelper.getAllRecords()
                                sendResponse(output, "HTTP/1.1 200 OK", "application/json", records.toString().toByteArray())
                            }
                        }
                        path == "/api/qa/files" -> {
                            val reportsDir = context.getExternalFilesDir("reports")
                            val jsonArray = JSONArray()
                            if (reportsDir != null && reportsDir.exists()) {
                                val files = reportsDir.listFiles()
                                if (files != null) {
                                    files.sortByDescending { it.lastModified() }
                                    for (file in files) {
                                        val item = JSONObject().apply {
                                            put("name", file.name)
                                            put("size", file.length())
                                            put("lastModified", file.lastModified())
                                            put("type", when {
                                                file.name.endsWith(".png") || file.name.endsWith(".jpg") -> "image"
                                                file.name.endsWith(".mp4") -> "video"
                                                else -> "text"
                                            })
                                        }
                                        jsonArray.put(item)
                                    }
                                }
                            }
                            sendResponse(output, "HTTP/1.1 200 OK", "application/json", jsonArray.toString().toByteArray())
                        }
                        path == "/api/qa/scripts" -> {
                            val scripts = dbHelper.getAllScripts()
                            sendResponse(output, "HTTP/1.1 200 OK", "application/json", scripts.toString().toByteArray())
                        }
                        path.startsWith("/qa/reports/") -> {
                            val fileName = path.substring("/qa/reports/".length)
                            val reportsDir = context.getExternalFilesDir("reports")
                            val file = File(reportsDir, fileName)
                            if (reportsDir != null && file.exists() && file.isFile) {
                                val contentType = when {
                                    fileName.endsWith(".png") -> "image/png"
                                    fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") -> "image/jpeg"
                                    fileName.endsWith(".mp4") -> "video/mp4"
                                    else -> "application/octet-stream"
                                }
                                try {
                                    val content = file.readBytes()
                                    sendResponse(output, "HTTP/1.1 200 OK", contentType, content)
                                } catch (e: Exception) {
                                    val errorHtml = "<html><body><h1>Error reading file</h1></body></html>"
                                    sendResponse(output, "HTTP/1.1 500 Internal Error", "text/html", errorHtml.toByteArray())
                                }
                            } else {
                                val errorHtml = "<html><body><h1>File Not Found</h1></body></html>"
                                sendResponse(output, "HTTP/1.1 404 Not Found", "text/html", errorHtml.toByteArray())
                            }
                        }
                        path == "/qa/reports.html" || path == "/reports" -> {
                            serveAssetFile(output, "reports.html", "text/html")
                        }
                        else -> {
                            val errorHtml = "<html><body><h1>404 Not Found</h1></body></html>"
                            sendResponse(output, "HTTP/1.1 404 Not Found", "text/html", errorHtml.toByteArray())
                        }
                    }
                }
                "POST" -> {
                    if (path == "/api/records") {
                        try {
                            val json = JSONObject(body)
                            val content = json.optString("content", "")
                            val lastUpdateBy = json.optString("lastUpdateBy", "System")

                            val record = dbHelper.insertRecord(content, lastUpdateBy)
                            if (record != null) {
                                WebserverLogger.log("Database: Record INSERTED (ID: ${record.optLong("id")}) by $lastUpdateBy")
                                sendResponse(output, "HTTP/1.1 201 Created", "application/json", record.toString().toByteArray())
                            } else {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Failed to create record\"}"
                                sendResponse(output, "HTTP/1.1 500 Internal Server Error", "application/json", errorJson.toByteArray())
                            }
                        } catch (e: Exception) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Invalid JSON body: ${e.message}\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        }
                    } else if (path == "/api/qa/script") {
                        try {
                            val json = JSONObject(body)
                            val script = json.optString("script", "")
                            WebserverLogger.log("Received script data: $script")
                            android.util.Log.i("QaWebServer", "Received script content:\n$script")

                            val successJson = "{\"status\":\"success\",\"message\":\"Script logged successfully\"}"
                            sendResponse(output, "HTTP/1.1 200 OK", "application/json", successJson.toByteArray())
                        } catch (e: Exception) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Invalid JSON body: ${e.message}\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        }
                    } else if (path == "/api/qa/openapp") {
                        try {
                            val json = JSONObject(body)
                            val query = json.optString("query", "")
                            WebserverLogger.log("Received openapp command for: '$query'")

                            val launched = hdisoft.app.core.utils.AppTool.openApp(context, query)
                            if (launched) {
                                val successJson = "{\"status\":\"success\",\"message\":\"App opened successfully\"}"
                                sendResponse(output, "HTTP/1.1 200 OK", "application/json", successJson.toByteArray())
                            } else {
                                val errorJson = "{\"status\":\"error\",\"message\":\"App matching '$query' not found\"}"
                                sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                            }
                        } catch (e: Exception) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Invalid JSON body: ${e.message}\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        }
                    } else if (path == "/api/qa/runscript") {
                        try {
                            val bodyJson = if (body.trim().startsWith("{")) JSONObject(body) else null
                            val scriptId = idParam ?: bodyJson?.optLong("id", -1L)?.takeIf { it != -1L }

                            val scriptContent = if (scriptId != null) {
                                val scriptObj = dbHelper.getScriptById(scriptId)
                                if (scriptObj != null) {
                                    dbHelper.incrementScriptRunCount(scriptId)
                                    scriptObj.getString("content")
                                } else {
                                    null
                                }
                            } else {
                                body
                            }

                            if (scriptContent == null) {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Script not found in database for ID: $scriptId\"}"
                                sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                            } else {
                                val executor = scriptExecutor
                                if (executor != null) {
                                    val responseJson = executor.executeScript(scriptContent)
                                    sendResponse(output, "HTTP/1.1 200 OK", "application/json", responseJson.toByteArray())
                                } else {
                                    val errorJson = "{\"status\":\"error\",\"message\":\"ScriptExecutor not registered\"}"
                                    sendResponse(output, "HTTP/1.1 503 Service Unavailable", "application/json", errorJson.toByteArray())
                                }
                            }
                        } catch (e: Exception) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Script execution error: ${e.message}\"}"
                            sendResponse(output, "HTTP/1.1 500 Internal Error", "application/json", errorJson.toByteArray())
                        }
                    } else if (path == "/api/qa/scripts") {
                        try {
                            val json = JSONObject(body)
                            val name = json.getString("name")
                            val content = json.getString("content")
                            val script = dbHelper.insertScript(name, content)
                            if (script != null) {
                                sendResponse(output, "HTTP/1.1 201 Created", "application/json", script.toString().toByteArray())
                            } else {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Failed to save script\"}"
                                sendResponse(output, "HTTP/1.1 500 Internal Error", "application/json", errorJson.toByteArray())
                            }
                        } catch (e: Exception) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Invalid request body: ${e.message}\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        }
                    } else {
                        val errorHtml = "<html><body><h1>404 Not Found</h1></body></html>"
                        sendResponse(output, "HTTP/1.1 404 Not Found", "text/html", errorHtml.toByteArray())
                    }
                }
                "PUT" -> {
                    if (path == "/api/records") {
                        if (idParam == null) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Missing 'id' query parameter\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        } else {
                            try {
                                val json = JSONObject(body)
                                val content = json.optString("content", "")
                                val lastUpdateBy = json.optString("lastUpdateBy", "System")

                                val record = dbHelper.updateRecord(idParam, content, lastUpdateBy)
                                if (record != null) {
                                    WebserverLogger.log("Database: Record UPDATED (ID: $idParam) by $lastUpdateBy")
                                    sendResponse(output, "HTTP/1.1 200 OK", "application/json", record.toString().toByteArray())
                                } else {
                                    val errorJson = "{\"status\":\"error\",\"message\":\"Record not found or update failed\"}"
                                    sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                                }
                            } catch (e: Exception) {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Invalid JSON body: ${e.message}\"}"
                                sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                            }
                        }
                    } else if (path == "/api/qa/scripts") {
                        if (idParam == null) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Missing 'id' query parameter\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        } else {
                            try {
                                val json = JSONObject(body)
                                val name = json.getString("name")
                                val content = json.getString("content")
                                val script = dbHelper.updateScript(idParam, name, content)
                                if (script != null) {
                                    sendResponse(output, "HTTP/1.1 200 OK", "application/json", script.toString().toByteArray())
                                } else {
                                    val errorJson = "{\"status\":\"error\",\"message\":\"Script not found or update failed\"}"
                                    sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                                }
                            } catch (e: Exception) {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Invalid JSON body: ${e.message}\"}"
                                sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                            }
                        }
                    } else {
                        val errorHtml = "<html><body><h1>404 Not Found</h1></body></html>"
                        sendResponse(output, "HTTP/1.1 404 Not Found", "text/html", errorHtml.toByteArray())
                    }
                }
                "DELETE" -> {
                    if (path == "/api/records") {
                        if (idParam == null) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Missing 'id' query parameter\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        } else {
                            val deleted = dbHelper.deleteRecord(idParam)
                            if (deleted) {
                                WebserverLogger.log("Database: Record DELETED (ID: $idParam)")
                                val successJson = "{\"status\":\"success\",\"message\":\"Record deleted successfully\"}"
                                sendResponse(output, "HTTP/1.1 200 OK", "application/json", successJson.toByteArray())
                            } else {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Record not found\"}"
                                sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                            }
                        }
                    } else if (path == "/api/qa/scripts") {
                        if (idParam == null) {
                            val errorJson = "{\"status\":\"error\",\"message\":\"Missing 'id' query parameter\"}"
                            sendResponse(output, "HTTP/1.1 400 Bad Request", "application/json", errorJson.toByteArray())
                        } else {
                            val deleted = dbHelper.deleteScript(idParam)
                            if (deleted) {
                                val successJson = "{\"status\":\"success\",\"message\":\"Script deleted successfully\"}"
                                sendResponse(output, "HTTP/1.1 200 OK", "application/json", successJson.toByteArray())
                            } else {
                                val errorJson = "{\"status\":\"error\",\"message\":\"Script not found\"}"
                                sendResponse(output, "HTTP/1.1 404 Not Found", "application/json", errorJson.toByteArray())
                            }
                        }
                    } else {
                        val errorHtml = "<html><body><h1>404 Not Found</h1></body></html>"
                        sendResponse(output, "HTTP/1.1 404 Not Found", "text/html", errorHtml.toByteArray())
                    }
                }
                else -> {
                    val errorHtml = "<html><body><h1>405 Method Not Allowed</h1></body></html>"
                    sendResponse(output, "HTTP/1.1 405 Method Not Allowed", "text/html", errorHtml.toByteArray())
                }
            }
        } catch (e: Exception) {
            WebserverLogger.log("Request error from $clientIp: ${e.message}")
            e.printStackTrace()
        } finally {
            try { output?.close() } catch (e: Exception) {}
            try { reader?.close() } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun sendResponse(output: OutputStream, status: String, contentType: String, content: ByteArray) {
        val headers = "$status\r\n" +
                "Content-Type: $contentType; charset=utf-8\r\n" +
                "Content-Length: ${content.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type\r\n" +
                "Connection: close\r\n\r\n"
        output.write(headers.toByteArray())
        output.write(content)
    }

    private fun serveAssetFile(output: OutputStream, assetPath: String, contentType: String) {
        try {
            val assetsInputStream = context.assets.open(assetPath)
            val content = assetsInputStream.readBytes()
            assetsInputStream.close()
            sendResponse(output, "HTTP/1.1 200 OK", contentType, content)
        } catch (e: Exception) {
            val errorHtml = "<html><body><h1>404 Not Found</h1><p>${e.message}</p></body></html>"
            sendResponse(output, "HTTP/1.1 404 Not Found", "text/html", errorHtml.toByteArray())
        }
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
