package hdisoft.app.qa

import android.content.Context
import hdisoft.app.qa.model.ActionModel
import hdisoft.app.webserver.WebserverLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ScriptTool {
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Parse a Gson-polymorphic action list and execute each action in order. */
    fun runScript(context: Context, jsonScript: String): String {
        WebserverLogger.log("ScriptTool: Parsing JSON script with Gson...")
        val actions = try {
            ActionModel.listFromJson(jsonScript)
        } catch (e: Exception) {
            val error = "Failed to parse script JSON: ${e.message}"
            WebserverLogger.log("ScriptTool error: $error")
            return "{\"status\":\"error\",\"message\":${jsonString(error)}}"
        }

        // A single action is the interactive "Run Action" path. Wait for doIt()
        // so the web UI receives the generated ID and the actual output value.
        if (actions.size == 1) {
            val action = actions.single()
            return try {
                WebserverLogger.log(
                    "ScriptTool: Running action -> " +
                        "id=${action.id}, name='${action.name}', category=${action.category.wireName}"
                )
                runBlocking(Dispatchers.Default) {
                    action.doIt(context)
                }
                WebserverLogger.log("ScriptTool: Action ${action.id} finished successfully.")
                "{\"status\":\"success\",\"message\":\"Action execution completed\",\"action\":${action.toJson()}}"
            } catch (e: Exception) {
                val error = "Action execution failed: ${e.message}"
                WebserverLogger.log("ScriptTool error: $error")
                "{\"status\":\"error\",\"message\":${jsonString(error)},\"action\":${action.toJson()}}"
            }
        }

        scope.launch {
            try {
                WebserverLogger.log("ScriptTool: Starting ${actions.size} action(s)...")
                actions.forEachIndexed { index, action ->
                    WebserverLogger.log(
                        "ScriptTool: ${index + 1}/${actions.size} -> " +
                            "id=${action.id}, name='${action.name}', category=${action.category.wireName}"
                    )
                    action.doIt(context)
                }
                WebserverLogger.log("ScriptTool: Execution finished successfully.")
            } catch (e: Exception) {
                WebserverLogger.log("ScriptTool exception during run: ${e.message}")
            }
        }

        return "{\"status\":\"success\",\"message\":\"Script execution started\"}"
    }

    private fun jsonString(value: String): String =
        ActionModel.gson.toJson(value)
}
