package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.NameAndVersion

object ApplicationDAO {
    val inMemoryDB = mutableMapOf(
        "figlet" to listOf(
            ApplicationDescription(
                tool = NameAndVersion("figlet", "1.0.0"),
                info = NameAndVersion("figlet", "1.0.0"),
                invocation = listOf(VariableInvocationParameter(listOf("text"))),
                parameters = listOf(
                    ApplicationParameter.Text(
                        name = "text",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Text",
                        description = "Some text to render with figlet"
                    )
                ),
                outputFileGlobs = listOf("output.txt")
            )
        )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
        inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<ApplicationDescription> = inMemoryDB.values.flatten()
}