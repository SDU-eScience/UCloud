package dk.sdu.cloud.app.store.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class NormalizedToolDescription(
    @JsonDeserialize(`as` = NameAndVersionImpl::class)
    val info: NameAndVersion,
    val container: String,
    val defaultNumberOfNodes: Int,
    val defaultTasksPerNode: Int,
    val defaultTimeAllocation: SimpleDuration,
    val requiredModules: List<String>,
    val authors: List<String>,
    val title: String,
    val description: String,
    val backend: ToolBackend,
    val license: String
) {
    override fun toString(): String {
        return "NormalizedToolDescription(info=$info, container='$container')"
    }
}

data class ToolReference(
    override val name: String,
    override val version: String,
    val tool: Tool?
) : NameAndVersion

data class Tool(
    val owner: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NormalizedToolDescription
)

