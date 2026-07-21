package hdisoft.app.qa

import android.content.Context
import android.os.Handler
import android.os.Looper
import hdisoft.app.core.utils.AppTool
import hdisoft.app.webserver.WebserverLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

object ScriptTool {
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Run a sequential list of steps described by a JSON array string.
     * Starts execution asynchronously and logs details to WebserverLogger.
     *
     * JSON Schema example:
     * [
     *   {"action": "open_app", "query": "Calendar"},
     *   {"action": "tap", "x": 500, "y": 800},
     *   {"action": "capture"},
     *   {"action": "record", "duration": 5000}
     * ]
     */
    fun runScript(context: Context, jsonScript: String): String {
        WebserverLogger.log("ScriptTool: Parsing JSON script...")
        val steps = try {
            JSONArray(jsonScript)
        } catch (e: Exception) {
            val errorMsg = "Failed to parse script JSON: ${e.message}"
            WebserverLogger.log("ScriptTool error: $errorMsg")
            return "{\"status\":\"error\",\"message\":\"$errorMsg\"}"
        }

        scope.launch {
            try {
                WebserverLogger.log("ScriptTool: Starting execution of ${steps.length()} steps...")
                for (i in 0 until steps.length()) {
                    val step = steps.getJSONObject(i)
                    val action = step.optString("action", "").trim().lowercase()
                    WebserverLogger.log("ScriptTool: Executing step ${i + 1}/${steps.length()} -> action: '$action'")

                    when (action) {
                        "open_app" -> {
                            val query = step.optString("query", "")
                            if (query.isNotEmpty()) {
                                val success = AppTool.openApp(context, query)
                                WebserverLogger.log("ScriptTool: open_app '$query' result: $success")
                            } else {
                                WebserverLogger.log("ScriptTool warning: open_app query is empty")
                            }
                            delay(2500) // Delay to let the app initialize completely
                        }
                        "tap" -> {
                            val x = step.optDouble("x", -1.0).toFloat()
                            val y = step.optDouble("y", -1.0).toFloat()
                            if (x >= 0 && y >= 0) {
                                // Execute gesture dispatching on the main UI thread via Accessibility service
                                Handler(Looper.getMainLooper()).post {
                                    val service = QaAccessibilityService.instance
                                    if (service != null) {
                                        val success = service.clickAt(x, y)
                                        WebserverLogger.log("ScriptTool: tap ($x, $y) dispatched. Result: $success")
                                    } else {
                                        WebserverLogger.log("ScriptTool error: Accessibility service not active for tap")
                                    }
                                }
                            } else {
                                WebserverLogger.log("ScriptTool warning: invalid tap coordinates ($x, $y)")
                            }
                            delay(1500) // Delay between gestures/renders
                        }
                        "capture" -> {
                            val service = QaAutomationService.instance
                            if (service != null) {
                                service.takeScreenShotExternal()
                                WebserverLogger.log("ScriptTool: capture screen request sent")
                            } else {
                                WebserverLogger.log("ScriptTool error: QaAutomationService not active for capture")
                            }
                            delay(2000) // Wait for layout hide, capture, file write, and layout restore
                        }
                        "record" -> {
                            val duration = step.optLong("duration", 5000L)
                            val service = QaAutomationService.instance
                            if (service != null) {
                                WebserverLogger.log("ScriptTool: Starting record screen for ${duration}ms")
                                service.startScreenRecordingExternal()
                                delay(duration)
                                service.stopScreenRecordingExternal()
                                WebserverLogger.log("ScriptTool: record screen stopped")
                            } else {
                                WebserverLogger.log("ScriptTool error: QaAutomationService not active for record")
                            }
                            delay(2000) // Wait for capture buffer finalization and file save
                        }
                        else -> {
                            WebserverLogger.log("ScriptTool warning: unknown action '$action'")
                        }
                    }
                }
                WebserverLogger.log("ScriptTool: Execution finished successfully.")
            } catch (e: Exception) {
                WebserverLogger.log("ScriptTool exception during run: ${e.message}")
            }
        }

        return "{\"status\":\"success\",\"message\":\"Script execution started\"}"
    }
}
