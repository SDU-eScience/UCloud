package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.NameAndVersion

object ApplicationDAO {
    val inMemoryDB = mutableMapOf(
        "hello" to listOf(
            ApplicationDescription(
                tool = NameAndVersion("hello_world", "1.0.0"),
                info = NameAndVersion("hello", "1.0.0"),
                numberOfNodes = null,
                tasksPerNode = null,
                maxTime = null,
                invocationTemplate = "--greeting \$greeting \$infile \$outfile",
                parameters = listOf(
                    ApplicationParameter.Text(
                        name = "greeting",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Greeting",
                        description = "A greeting"
                    ),

                    ApplicationParameter.InputFile(
                        name = "infile",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Input file",
                        description = "An input file"
                    ),

                    ApplicationParameter.OutputFile(
                        name = "outfile",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Output file",
                        description = "An output file"
                    )
                )
            )
        ),
        "figlet" to listOf(
            ApplicationDescription(
                tool = NameAndVersion("figlet", "1.0.0"),
                info = NameAndVersion("figlet", "1.0.0"),
                numberOfNodes = null,
                tasksPerNode = null,
                maxTime = null,
                invocationTemplate = "\$text",
                parameters = listOf(
                    ApplicationParameter.Text(
                        name = "text",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Text",
                        description = "Some text to render with figlet"
                    ),
                    ApplicationParameter.OutputFile(
                        name = "output",
                        optional = false,
                        defaultValue = null,
                        prettyName = "Output File",
                        description = "Output File"
                    )
                )
            )
        )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
        inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<ApplicationDescription> = inMemoryDB.values.flatten()
}