package dk.sdu.cloud.app

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.service.JsonSerde.jsonSerde
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SBatchGeneratorTest {
    @Test
    fun testWithNoParams() {
        ToolDAO.inMemoryDB["test"] = listOf(
            ToolDescription(
                title = "hello",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "hello",
                info = NameAndVersion("test", "1.0.0"),
                container = "container-name",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(1, 0, 0),
                requiredModules = emptyList()
            )
        )

        ApplicationDAO.inMemoryDB["app"] = listOf(
            NormalizedApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Figlet",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                tool = NameAndVersion("test", "1.0.0"),
                info = NameAndVersion("app", "1.0.0"),
                invocation = listOf(WordInvocationParameter("hello")),
                parameters = emptyList(),
                outputFileGlobs = emptyList()
            )
        )

        val generator = SBatchGenerator()

        val app = ApplicationDAO.findByNameAndVersion("app", "1.0.0")!!
        val request = AppRequest.Start(app.info, emptyMap())
        val job = generator.generate(app, request, "")
        val lines = job.lines()

        val runLine = lines.find { it.startsWith("srun singularity") }
        assertThat(runLine, containsString("\"container-name\""))

        assertThat(lines, hasItem(containsString("#SBATCH --nodes 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --ntasks-per-node 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --time 01:00:00")))

        assertThat(lines, hasItem(containsString("module add \"singularity\"")))
    }

    @Test
    fun testWithFileParameters() {
        ToolDAO.inMemoryDB["hello_world"] = listOf(
            ToolDescription(
                title = "hello",
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
        )

        ApplicationDAO.inMemoryDB["hello"] = listOf(
            NormalizedApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Figlet",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                tool = NameAndVersion("hello_world", "1.0.0"),
                info = NameAndVersion("hello", "1.0.0"),
                invocation = listOf(
                    VariableInvocationParameter(listOf("greeting"), prefixVariable = "--greeting "),
                    VariableInvocationParameter(listOf("infile"))
                ),
                parameters = listOf(
                    ApplicationParameter.Text("greeting", false, null, "greeting", "greeting"),
                    ApplicationParameter.InputFile("infile", false, null, "infile", "infile")
                ),
                outputFileGlobs = emptyList()
            )
        )

        val serde = jsonSerde<Map<String, Any>>()
        val parametersJson = """
        {
            "greeting": "test",
            "infile": {
                "source": "storage://tempZone/home/rods/infile",
                "destination": "files/afile.txt"
            }
        }
        """.trimIndent()

        val gen = SBatchGenerator()
        val parameters = serde.deserializer().deserialize("", parametersJson.toByteArray())

        val app = ApplicationDAO.findAllByName("hello").first()
        val command = AppRequest.Start(app.info, parameters)
        val jobLines = gen.generate(app, command, "/test/a/b/c").lines()

        val srunLine = jobLines.find { it.startsWith("srun singularity") }
        assertThat(
            srunLine, containsString(
                """
            --greeting "test" "files/afile.txt"
        """.trimIndent()
            )
        )
    }


    @Test
    fun testWithSeveralFileParameters() {
        ToolDAO.inMemoryDB["hello_world"] = listOf(
            ToolDescription(
                title = "hello",
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
        )

        ApplicationDAO.inMemoryDB["hello"] = listOf(
            NormalizedApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                prettyName = "Figlet",
                createdAt = 1519910207000L,
                modifiedAt = 1519910207000L,
                description = "Render some text!",
                tool = NameAndVersion("hello_world", "1.0.0"),
                info = NameAndVersion("hello", "1.0.0"),
                invocation = listOf(
                    VariableInvocationParameter(listOf("greeting", "boo"), prefixVariable = "--greeting "),
                    VariableInvocationParameter(listOf("infile"))
                ),
                parameters = listOf(
                    ApplicationParameter.Text("greeting", false, null, "greeting", "greeting"),
                    ApplicationParameter.Bool("boo", false, null, "boo", "boo", trueValue = "yes", falseValue = "no"),
                    ApplicationParameter.InputFile("infile", false, null, "infile", "infile")
                ),
                outputFileGlobs = emptyList()
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
            "boo": true
        }
        """.trimIndent()

        val gen = SBatchGenerator()
        val parameters = serde.deserializer().deserialize("", parametersJson.toByteArray())

        val app = ApplicationDAO.findAllByName("hello").first()
        val request = AppRequest.Start(app.info, parameters)
        val jobLines = gen.generate(app, request, "/test/a/b/c").lines()

        val srunLine = jobLines.find { it.startsWith("srun singularity") }
        assertThat(
            srunLine, containsString(
                """
            --greeting "test" --greeting "yes" "files/afile.txt"
        """.trimIndent()
            )
        )
    }
}