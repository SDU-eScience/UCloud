package org.escienceclud.abc

import org.esciencecloud.abc.api.*
import org.esciencecloud.abc.services.ApplicationDAO
import org.esciencecloud.abc.services.SBatchGenerator
import org.esciencecloud.abc.services.ToolDAO
import org.esciencecloud.service.JsonSerde.jsonSerde
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SBatchGeneratorTest {
    @Test
    fun testWithNoParams() {
        ToolDAO.inMemoryDB["test"] = listOf(ToolDescription(
                info = NameAndVersion("test", "1.0.0"),
                container = "container-name",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(1, 0, 0),
                requiredModules = emptyList()
        ))

        ApplicationDAO.inMemoryDB["app"] = listOf(ApplicationDescription(
                tool = NameAndVersion("test", "1.0.0"),
                info = NameAndVersion("app", "1.0.0"),
                numberOfNodes = null,
                tasksPerNode = null,
                maxTime = null,
                invocationTemplate = "hello",
                parameters = emptyList()
        ))

        val generator = SBatchGenerator("test@test")

        val job = generator.generate(ApplicationDAO.findByNameAndVersion("app", "1.0.0")!!, emptyMap(), "")
        val lines = job.lines()

        val runLine = lines.find { it.startsWith("srun singularity") }
        assertThat(runLine, containsString("\"container-name\""))

        assertThat(lines, hasItem(containsString("#SBATCH --nodes 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --ntasks-per-node 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --time 01:00:00")))

        assertThat(lines, hasItem(containsString("module add singularity")))
    }

    @Test
    fun testWithFileParameters() {
        ToolDAO.inMemoryDB["hello_world"] = listOf(
                ToolDescription(
                        info = NameAndVersion("hello_world", "1.0.0"),
                        container = "hello.simg",
                        defaultNumberOfNodes = 1,
                        defaultTasksPerNode = 1,
                        defaultMaxTime = SimpleDuration(hours = 0, minutes = 1, seconds = 0),
                        requiredModules = emptyList()
                )
        )

        ApplicationDAO.inMemoryDB["hello"] = listOf(
                ApplicationDescription(
                        tool = NameAndVersion("hello_world", "1.0.0"),
                        info = NameAndVersion("hello", "1.0.0"),
                        numberOfNodes = null,
                        tasksPerNode = null,
                        maxTime = null,
                        invocationTemplate = "--greeting \$greeting \$infile \$outfile",
                        parameters = listOf(
                                ApplicationParameter.Text("greeting", false, null, "greeting", "greeting"),
                                ApplicationParameter.InputFile("infile", false, null, "infile", "infile"),
                                ApplicationParameter.OutputFile("outfile", false, null, "outfile", "outfile")
                        )
                )
        )

        val serde = jsonSerde<Map<String, Any>>()
        val parametersJson = """
        {
            "greeting": "test",
            "infile": {
                "source": "storage://tempZone/home/rods/infile",
                "destination": "files/afile.txt"
            },
            "outfile": {
                "destination": "storage://tempZone/home/rods/infile",
                "source": "files/bfile.txt"
            }
        }
        """.trimIndent()

        val gen = SBatchGenerator("sdu.esci.dev@gmail.com")
        val parameters = serde.deserializer().deserialize("", parametersJson.toByteArray())

        val app = ApplicationDAO.findAllByName("hello").first()
        val jobLines = gen.generate(app, parameters, "/test/a/b/c").lines()

        val srunLine = jobLines.find { it.startsWith("srun singularity") }
        assertThat(srunLine, endsWith("""
            "--greeting" "test" "files/afile.txt" "files/bfile.txt"
        """.trimIndent()))
    }
}