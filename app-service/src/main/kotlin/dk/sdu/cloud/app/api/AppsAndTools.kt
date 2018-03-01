package dk.sdu.cloud.app.api

import dk.sdu.cloud.app.services.InvocationParameter

data class ApplicationDescription(
    val tool: NameAndVersion,
    val info: NameAndVersion,
    val authors: List<String>,
    val prettyName: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String,
    val invocation: List<InvocationParameter>,
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
    val requiredModules: List<String>,
    val authors: List<String>,
    val prettyName: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String
)
