package org.esciencecloud.abc

import org.esciencecloud.abc.api.ApplicationDescription
import org.esciencecloud.abc.api.ApplicationParameter
import org.esciencecloud.abc.api.NameAndVersion

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
                                    ApplicationParameter.Text("greeting"),
                                    ApplicationParameter.InputFile("infile"),
                                    ApplicationParameter.OutputFile("outfile")
                            )
                    )
            )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
            inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()
}