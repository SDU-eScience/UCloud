package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.abacus.services.SBatchGenerator
import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.FileTransferDescription
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.StringApplicationParameter
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VariableInvocationParameter
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
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

    private fun applicationWithFile(tool: Tool = newTool()): Application {
        return Application(
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
    }

    private fun simpleJob(app: Application = simpleApp()): VerifiedJob {
        return VerifiedJob(
            app,
            emptyList(),
            "testing",
            "owner",
            1,
            1,
            SimpleDuration(1, 0, 0),
            VerifiedJobInput(emptyMap()),
            "",
            JobState.PREPARED
        )
    }

    private val generator = SBatchGenerator()

    @Test
    fun testWithNoParams() {
        val job = simpleJob().copy(
            maxTime = SimpleDuration(1, 0, 0)
        )

        val generatedJob = generator.generate(job, "")
        val lines = generatedJob.lines()

        val runLine = lines.find { it.startsWith("srun singularity") }
        assertThat(runLine, containsString("\"container-name\""))

        assertThat(lines, hasItem(containsString("#SBATCH --nodes 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --ntasks-per-node 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --time 01:00:00")))

        assertThat(lines, hasItem(containsString("module add \"singularity\"")))
    }

    @Test
    fun testWithFileParameters() {
        val app = applicationWithFile()
        val parameters = app.description.parameters
        val textParam = parameters.asSequence().filterIsInstance<ApplicationParameter.Text>()
            .first()

        val inputParam = parameters.asSequence().filterIsInstance<ApplicationParameter.InputFile>()
            .first()

        val destination = "/some/destination"
        val textValue = "test"
        val job = simpleJob(app).copy(
            jobInput = VerifiedJobInput(
                mapOf(
                    textParam.name to StringApplicationParameter(textValue),
                    inputParam.name to FileTransferDescription("/some/source", destination)
                )
            )
        )
        val jobLines = generator.generate(job, "").lines()

        val srunLine = jobLines.find { it.startsWith("srun singularity") }
        assertThat(
            srunLine, containsString(
                """
                    --greeting "$textValue" "$destination"
                """.trimIndent()
            )
        )
    }
}
