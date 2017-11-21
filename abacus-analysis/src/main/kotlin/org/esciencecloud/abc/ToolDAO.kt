package org.esciencecloud.abc

import org.esciencecloud.abc.api.NameAndVersion
import org.esciencecloud.abc.api.SimpleDuration
import org.esciencecloud.abc.api.ToolDescription

object ToolDAO {
    val inMemoryDB = mutableMapOf(
            "hello_world" to listOf(
                    ToolDescription(
                            info = NameAndVersion("hello_world", "1.0.0"),
                            container = "hello.simg",
                            defaultNumberOfNodes = 1,
                            defaultTasksPerNode = 1,
                            defaultMaxTime = SimpleDuration(hours = 0, minutes = 1, seconds = 0),
                            requiredModules = emptyList()
                    )
            )
    )

    fun findByNameAndVersion(name: String, version: String): ToolDescription? =
            inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ToolDescription> = inMemoryDB[name] ?: emptyList()
}