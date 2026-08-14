package hdisoft.app.qa.model

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.concurrent.atomic.AtomicInteger

enum class ActionCategory(val wireName: String) {
    CORE("core"),
    OTHER("other");

    companion object {
        fun fromWireName(value: String?): ActionCategory? =
            entries.firstOrNull { it.wireName.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * Polymorphic executable action.
 *
 * Only [name] is required in input JSON. [id] is generated locally and cannot
 * be supplied by a client. Input/output intentionally remain dynamic.
 */
abstract class ActionModel internal constructor(
    val name: String,
    val displayName: String = name,
    val description: String? = null,
    val category: ActionCategory,
    val input: Any? = null,
    var output: Any? = null
) {
    val id: Int = nextId.getAndIncrement()

    val isValid: Boolean
        get() = name.isNotBlank()

    /** Execute this action and update [output] with the implementation result. */
    abstract suspend fun doIt(context: Context): Any?

    fun toJson(): String = gson.toJson(this, ActionModel::class.java)

    protected fun inputMap(): Map<*, *>? = input as? Map<*, *>

    companion object {
        private val nextId = AtomicInteger(1)
        private val listType = object : TypeToken<List<ActionModel>>() {}.type

        internal val gson: Gson = GsonBuilder()
            .registerTypeHierarchyAdapter(ActionModel::class.java, ActionModelJsonAdapter())
            .serializeNulls()
            .create()

        fun fromJson(json: String): ActionModel =
            gson.fromJson(json, ActionModel::class.java)

        fun listFromJson(json: String): List<ActionModel> =
            gson.fromJson(json, listType)

        fun listToJson(actions: List<ActionModel>): String =
            gson.toJson(actions, listType)

        fun definitionsToJson(): String =
            gson.toJson(ActionRegistry.definitions().map { it.toWebModel() })

        fun create(
            name: String,
            displayName: String = name,
            description: String? = null,
            category: ActionCategory? = null,
            input: Any? = null,
            output: Any? = null
        ): ActionModel = createTyped(
            name = name.trim(),
            displayName = displayName.ifBlank { name },
            description = description,
            category = category ?: defaultCategory(name),
            input = input,
            output = output
        )

        internal fun defaultCategory(name: String): ActionCategory =
            ActionRegistry.categoryFor(name) ?: ActionCategory.OTHER

        internal fun createTyped(
            name: String,
            displayName: String,
            description: String?,
            category: ActionCategory,
            input: Any?,
            output: Any?
        ): ActionModel = ActionRegistry.create(
            name, displayName, description, category, input, output
        ) ?: OtherAction(name, displayName, description, category, input, output)
    }
}

/** Gson serializer/deserializer selecting the concrete action class by name. */
private class ActionModelJsonAdapter :
    JsonSerializer<ActionModel>,
    JsonDeserializer<ActionModel> {

    override fun serialize(
        source: ActionModel,
        typeOfSource: Type,
        context: JsonSerializationContext
    ): JsonElement = JsonObject().apply {
        addProperty("id", source.id)
        addProperty("name", source.name)
        addProperty("displayName", source.displayName)
        add("description", context.serialize(source.description))
        addProperty("category", source.category.wireName)
        add("input", context.serialize(source.input))
        add("output", context.serialize(source.output))
    }

    override fun deserialize(
        json: JsonElement,
        typeOfTarget: Type,
        context: JsonDeserializationContext
    ): ActionModel {
        val source = json.asJsonObject
        val canonicalName = source.stringOrNull("name")?.trim().orEmpty()
        val legacyName = source.stringOrNull("action")?.trim().orEmpty()
        val name = canonicalName.ifEmpty { legacyName }
        if (name.isBlank()) throw JsonParseException("Action name must not be blank")

        val displayName = source.stringOrNull("displayName")?.trim().orEmpty().ifEmpty { name }
        val description = source.stringOrNull("description")
        val category = ActionCategory.fromWireName(source.stringOrNull("category"))
            ?: ActionModel.defaultCategory(name)
        val inputElement = when {
            source.has("input") -> source.get("input")
            legacyName.isNotEmpty() -> source
            else -> null
        }

        return ActionModel.createTyped(
            name = name,
            displayName = displayName,
            description = description,
            category = category,
            input = inputElement?.takeUnless { it.isJsonNull }
                ?.let { context.deserialize<Any>(it, Any::class.java) },
            output = source.get("output")?.takeUnless { it.isJsonNull }
                ?.let { context.deserialize<Any>(it, Any::class.java) }
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeUnless { it.isJsonNull }?.asString
}
