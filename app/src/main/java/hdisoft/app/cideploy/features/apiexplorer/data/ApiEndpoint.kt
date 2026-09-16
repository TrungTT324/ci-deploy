package hdisoft.app.cideploy.features.apiexplorer.data

data class ApiEndpoint(
    val id: String,
    val title: String,
    val method: String,
    val pathTemplate: String,
    val sampleBody: String? = null
) {
    val hasBody: Boolean get() = method == "POST" || method == "PATCH"

    /** Names of the `{placeholder}` segments in [pathTemplate], in order. */
    fun paramNames(): List<String> {
        val regex = Regex("\\{(\\w+)\\}")
        return regex.findAll(pathTemplate).map { it.groupValues[1] }.toList()
    }

    fun resolvePath(paramValues: Map<String, String>): String {
        var resolved = pathTemplate
        for ((name, value) in paramValues) {
            resolved = resolved.replace("{$name}", value)
        }
        return resolved
    }
}
