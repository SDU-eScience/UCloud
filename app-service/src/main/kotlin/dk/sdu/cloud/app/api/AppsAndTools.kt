package dk.sdu.cloud.app.api

data class ApplicationDescription(
    val tool: NameAndVersion,
    val info: NameAndVersion,
    val numberOfNodes: String?,
    val tasksPerNode: String?,
    val maxTime: String?,
    val invocationTemplate: String,
    // TODO We cannot have duplicates on param name!
    val parameters: List<ApplicationParameter<*>>,
    val outputFileGlobs: List<String>
)

data class NameAndVersion(val name: String, val version: String) {
    override fun toString() = "$name@$version"
}

data class ToolDescription(
    val info: NameAndVersion,
    val container: String,
    val defaultNumberOfNodes: Int,
    val defaultTasksPerNode: Int,
    val defaultMaxTime: SimpleDuration,
    val requiredModules: List<String>
)
