package dk.sdu.cloud.abc.services

import dk.sdu.cloud.abc.api.ApplicationDescription
import dk.sdu.cloud.abc.api.ApplicationParameter
import dk.sdu.cloud.abc.api.NameAndVersion

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
            )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
            inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()

    fun all(): List<ApplicationDescription> = inMemoryDB.values.flatten()
}