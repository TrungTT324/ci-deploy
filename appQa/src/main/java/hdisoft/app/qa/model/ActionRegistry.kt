package hdisoft.app.qa.model

/** Public metadata returned by GET /api/qa/steps and rendered by Action List. */
data class ActionDefinition(
    val name: String,
    val displayName: String,
    val description: String,
    val category: ActionCategory,
    val className: String,
    val input: Any?,
    val output: String = "Boolean"
) {
    fun toWebModel(): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "displayName" to displayName,
        "description" to description,
        "category" to category.wireName,
        "className" to className,
        "input" to input,
        "output" to output,
        "example" to linkedMapOf(
            "name" to name,
            "displayName" to displayName,
            "description" to description,
            "category" to category.wireName,
            "input" to input,
            "output" to null
        )
    )
}

internal typealias ActionFactory = (
    displayName: String,
    description: String?,
    category: ActionCategory,
    input: Any?,
    output: Any?
) -> ActionModel

/**
 * Single source of truth for executable built-in actions.
 *
 * Register a new core action here once; Gson dispatch and GET /api/qa/steps
 * automatically use the same entry, so the Web Action List needs no edit.
 */
object ActionRegistry {
    private data class RegisteredAction(
        val definition: ActionDefinition,
        val factory: ActionFactory
    )

    private val actions = linkedMapOf<String, RegisteredAction>()

    init {
        registerCore(
            "open_app", "Open App",
            "Open an installed Android application by label or package name.",
            "OpenAppAction", mapOf("query" to "Calendar"), ::OpenAppAction
        )
        registerCore(
            "tap", "Tap", "Tap an absolute screen coordinate through Accessibility.",
            "TapAction", mapOf("x" to 102, "y" to 399), ::TapAction
        )
        registerCore(
            "home", "Home", "Press the Android Home system navigation button.",
            "HomeAction", null, ::HomeAction
        )
        registerCore(
            "back", "Back", "Press the Android Back system navigation button.",
            "BackAction", null, ::BackAction
        )
        registerCore(
            "recents", "Recents", "Open the Android recent apps overview.",
            "RecentsAction", null, ::RecentsAction
        )
        registerCore(
            "wait", "Wait", "Pause script execution for a configured duration.",
            "WaitAction", mapOf("duration" to 1000), ::WaitAction
        )
        registerCore(
            "clear_recents", "Clear Recent Apps",
            "Remove all app tasks exposed by the device Recents screen through Accessibility.",
            "ClearRecentsAction", null, ::ClearRecentsAction, "Clear-recents result"
        )
        registerCore(
            "capture", "Capture Screen",
            "Capture the current display and save a PNG to reports and Gallery.",
            "CaptureAction", null, ::CaptureAction, "Image URL"
        )
        registerCore(
            "record", "Record Screen",
            "Record the screen for a configured duration and save an MP4 report.",
            "RecordAction", mapOf("duration" to 5000), ::RecordAction, "Video URL"
        )
    }

    internal fun registerCore(
        name: String,
        displayName: String,
        description: String,
        className: String,
        input: Any?,
        factory: ActionFactory,
        output: String = "Boolean"
    ) {
        val key = name.trim().lowercase()
        require(key.isNotEmpty()) { "Core action name must not be blank" }
        require(key !in actions) { "Core action '$key' is already registered" }
        actions[key] = RegisteredAction(
            ActionDefinition(
                name = key,
                displayName = displayName,
                description = description,
                category = ActionCategory.CORE,
                className = className,
                input = input,
                output = output
            ),
            factory
        )
    }

    fun definitions(): List<ActionDefinition> = actions.values.map { it.definition }

    internal fun categoryFor(name: String): ActionCategory? =
        actions[name.trim().lowercase()]?.definition?.category

    internal fun create(
        name: String,
        displayName: String,
        description: String?,
        category: ActionCategory,
        input: Any?,
        output: Any?
    ): ActionModel? = actions[name.trim().lowercase()]?.factory?.invoke(
        displayName,
        description,
        category,
        input,
        output
    )
}
