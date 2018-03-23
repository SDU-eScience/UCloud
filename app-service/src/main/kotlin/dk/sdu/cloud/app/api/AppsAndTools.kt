package dk.sdu.cloud.app.api

import dk.sdu.cloud.app.services.InvocationParameter

data class ApplicationSummary(
    val tool: NameAndVersion,
    val info: NameAndVersion,
    val prettyName: String,
    val authors: List<String>,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String
)

data class ApplicationWithOptionalDependencies(
    val application: ApplicationDescription,
    val tool: ToolDescription?
)

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
) {
    fun toSummary(): ApplicationSummary = ApplicationSummary(
        tool, info, prettyName, authors, createdAt, modifiedAt, description
    )
}

enum class ToolBackend {
    SINGULARITY,
    UDOCKER
}

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
    val description: String,
    val backend: ToolBackend = ToolBackend.SINGULARITY
)
