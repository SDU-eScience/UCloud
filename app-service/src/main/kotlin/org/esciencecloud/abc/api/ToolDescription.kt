package org.esciencecloud.abc.api

data class ToolDescription(
        val info: NameAndVersion,
        val container: String,
        val defaultNumberOfNodes: Int,
        val defaultTasksPerNode: Int,
        val defaultMaxTime: SimpleDuration,
        val requiredModules: List<String>
)