package hdisoft.app.qa.model

import android.accessibilityservice.AccessibilityService
import android.content.Context
import hdisoft.app.core.utils.AppTool
import hdisoft.app.qa.QaAccessibilityService
import hdisoft.app.qa.QaAutomationService
import hdisoft.app.qa.ClearRecentsTool
import hdisoft.app.webserver.WebserverLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class OpenAppAction internal constructor(
    displayName: String = "open_app",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : ActionModel("open_app", displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        val query = inputMap()?.get("query") as? String ?: ""
        output = if (query.isNotEmpty()) AppTool.openApp(context, query) else false
        WebserverLogger.log("OpenAppAction#$id: query='$query', output=$output")
        delay(2500)
        return output
    }
}

class TapAction internal constructor(
    displayName: String = "tap",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : ActionModel("tap", displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        val values = inputMap()
        val x = (values?.get("x") as? Number)?.toFloat() ?: -1f
        val y = (values?.get("y") as? Number)?.toFloat() ?: -1f
        output = if (x >= 0 && y >= 0) {
            suspendCancellableCoroutine { continuation ->
                val service = QaAccessibilityService.instance
                if (service == null) {
                    continuation.resume(false)
                } else {
                    val accepted = service.clickAt(x, y) { completed ->
                        if (continuation.isActive) continuation.resume(completed)
                    }
                    if (!accepted && continuation.isActive) continuation.resume(false)
                }
            }
        } else {
            false
        }
        WebserverLogger.log("TapAction#$id: ($x,$y), output=$output")
        delay(1500)
        return output
    }
}

class CaptureAction internal constructor(
    displayName: String = "capture",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : ActionModel("capture", displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        output = QaAutomationService.instance?.takeScreenShotExternal()
        WebserverLogger.log("CaptureAction#$id: output=$output")
        return output
    }
}

class RecordAction internal constructor(
    displayName: String = "record",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : ActionModel("record", displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        val duration = (inputMap()?.get("duration") as? Number)?.toLong() ?: 5000L
        output = QaAutomationService.instance?.let {
            if (!it.startScreenRecordingExternal()) {
                null
            } else {
                delay(duration.coerceAtLeast(1L))
                it.stopScreenRecordingExternal()
            }
        }
        WebserverLogger.log("RecordAction#$id: duration=$duration, output=$output")
        delay(2000)
        return output
    }
}

class WaitAction internal constructor(
    displayName: String = "wait",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : ActionModel("wait", displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        val duration = (inputMap()?.get("duration") as? Number)?.toLong() ?: 1000L
        delay(duration.coerceAtLeast(0L))
        output = true
        WebserverLogger.log("WaitAction#$id: duration=$duration, output=$output")
        return output
    }
}

class ClearRecentsAction internal constructor(
    displayName: String = "clear_recents",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : ActionModel("clear_recents", displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        output = ClearRecentsTool.clear(context)
        WebserverLogger.log("ClearRecentsAction#$id: output=${toJson()}")
        delay(500)
        return output
    }
}

abstract class NavigationAction internal constructor(
    name: String,
    displayName: String,
    description: String?,
    category: ActionCategory,
    input: Any?,
    output: Any?,
    private val globalAction: Int
) : ActionModel(name, displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        output = withContext(Dispatchers.Main) {
            QaAccessibilityService.instance?.performNavigation(globalAction) ?: false
        }
        WebserverLogger.log("${javaClass.simpleName}#$id: output=$output")
        delay(500)
        return output
    }
}

class HomeAction internal constructor(
    displayName: String = "home",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : NavigationAction(
    "home", displayName, description, category, input, output,
    AccessibilityService.GLOBAL_ACTION_HOME
)

class BackAction internal constructor(
    displayName: String = "back",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : NavigationAction(
    "back", displayName, description, category, input, output,
    AccessibilityService.GLOBAL_ACTION_BACK
)

class RecentsAction internal constructor(
    displayName: String = "recents",
    description: String? = null,
    category: ActionCategory = ActionCategory.CORE,
    input: Any? = null,
    output: Any? = null
) : NavigationAction(
    "recents", displayName, description, category, input, output,
    AccessibilityService.GLOBAL_ACTION_RECENTS
)

class OtherAction internal constructor(
    actionName: String,
    displayName: String = actionName,
    description: String? = null,
    category: ActionCategory = ActionCategory.OTHER,
    input: Any? = null,
    output: Any? = null
) : ActionModel(actionName, displayName, description, category, input, output) {
    override suspend fun doIt(context: Context): Any? {
        output = false
        WebserverLogger.log("OtherAction#$id: no implementation for name='$name'")
        return output
    }
}
