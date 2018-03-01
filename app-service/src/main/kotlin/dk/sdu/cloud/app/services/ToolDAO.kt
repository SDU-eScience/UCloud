package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolDescription

object ToolDAO {
    val inMemoryDB = mutableMapOf(
        "hello_world" to listOf(
            ToolDescription(
                prettyName = "hello",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "hello",
                info = NameAndVersion("hello_world", "1.0.0"),
                container = "hello.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(hours = 0, minutes = 1, seconds = 0),
                requiredModules = emptyList()
            )
        ),

        "figlet" to listOf(
            ToolDescription(
                prettyName = "Figlet",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                info = NameAndVersion("figlet", "1.0.0"),
                container = "figlet.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(0, 1, 0),
                requiredModules = emptyList()
            )
        ),

        "parms" to listOf(
            ToolDescription(
                prettyName = "parms",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "parms",
                info = NameAndVersion("parms", "1.0.0"),
                container = "parms.simg",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(0, 10, 0),
                requiredModules = emptyList()
            )
        )
    )

    fun findByNameAndVersion(name: String, version: String): ToolDescription? =
        inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ToolDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<ToolDescription> = inMemoryDB.values.flatten()
}