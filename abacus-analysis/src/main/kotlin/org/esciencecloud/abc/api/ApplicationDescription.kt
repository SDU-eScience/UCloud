package org.esciencecloud.abc.api

data class ApplicationDescription(
        val tool: NameAndVersion,
        val info: NameAndVersion,
        val numberOfNodes: String?,
        val tasksPerNode: String?,
        val maxTime: String?,
        val invocationTemplate: String,
        // TODO We cannot have duplicates on param name!
        val parameters: List<ApplicationParameter<*>>
)