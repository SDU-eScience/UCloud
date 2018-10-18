package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VariableInvocationParameter
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.service.JsonSerde.jsonSerde
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SBatchGeneratorTest {
    private fun newTool(): Tool {
        return Tool(
            "foo",
            0L,
            0L,
            NormalizedToolDescription(
                title = "hello",
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                description = "hello",
                info = NameAndVersion("test", "1.0.0"),
                container = "container-name",
                defaultNumberOfNodes = 1,
                defaultTasksPerNode = 1,
                defaultMaxTime = SimpleDuration(1, 0, 0),
                requiredModules = emptyList(),
                backend = ToolBackend.SINGULARITY
            )
        )
    }

    private fun simpleApp(): Application {
        val tool = newTool()

        return Application(
            "foo",
            0L,
            0L,
            NormalizedApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                title = "Figlet",
                description = "Render some text!",
                tool = NameAndVersion("test", "1.0.0"),
                info = NameAndVersion("app", "1.0.0"),
                invocation = listOf(WordInvocationParameter("hello")),
                parameters = emptyList(),
                outputFileGlobs = emptyList(),
                tags = listOf()
            ),
            tool
        )
    }


    @Test
    fun testWithNoParams() {
        val app = simpleApp()
        val generator = SBatchGenerator()
        val request = AppRequest.Start(app.description.info, emptyMap())
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
        val tool = newTool()

        val app = Application(
            "foo",
            0L,
            0L,
            NormalizedApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                title = "Figlet",
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
                outputFileGlobs = emptyList(),
                tags = listOf()
            ),
            tool
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

        val command = AppRequest.Start(app.description.info, parameters)
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
        val tool = newTool()

        val app = Application(
            "foo",
            0L,
            0L,
            NormalizedApplicationDescription(
                authors = listOf("Dan Sebastian Thrane <dthrane@imada.sdu.dk>"),
                title = "Figlet",
                description = "Render some text!",
                tool = tool.description.info,
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
                outputFileGlobs = emptyList(),
                tags = listOf()
            ),
            tool
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

        val request = AppRequest.Start(app.description.info, parameters)
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
