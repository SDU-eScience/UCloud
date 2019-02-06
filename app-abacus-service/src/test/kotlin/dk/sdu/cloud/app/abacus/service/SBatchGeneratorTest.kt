package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.abacus.services.SBatchGenerator
import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.FileTransferDescription
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.StringApplicationParameter
import dk.sdu.cloud.app.api.VariableInvocationParameter
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter
import dk.sdu.cloud.service.test.assertThatInstance
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class SBatchGeneratorTest {
    private fun simpleApp(): Application {
        return normAppDesc
            .withNameAndVersion("app", "1.0.0")
            .withInvocation(listOf(WordInvocationParameter("hello")))
    }

    private fun applicationWithFile(): Application {
        return normAppDesc
            .withNameAndVersion("hello", "1.0.0")
            .withInvocation(
                listOf(
                    VariableInvocationParameter(listOf("greeting"), prefixVariable = "--greeting "),
                    VariableInvocationParameter(listOf("infile"))
                )
            )
            .withParameters(
                listOf(
                    ApplicationParameter.Text("greeting", false, null, "greeting", "greeting"),
                    ApplicationParameter.InputFile("infile", false, null, "infile", "infile")
                )
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
            JobState.PREPARED,
            ""
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

        val runLine = lines.find { it.trim().startsWith("srun singularity") }
        assertThat(runLine, containsString(job.application.invocation.tool.tool!!.description.container))

        assertThat(lines, hasItem(containsString("#SBATCH --nodes 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --ntasks-per-node 1")))
        assertThat(lines, hasItem(containsString("#SBATCH --time 01:00:00")))

        assertThat(lines, hasItem(containsString("module add \"singularity\"")))
    }

    @Test
    fun testWithFileParameters() {
        val app = applicationWithFile()
        val parameters = app.invocation.parameters
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

        val srunLine = jobLines.find { it.trim().startsWith("srun singularity") }
        assertThatInstance(
            srunLine,
            matcher = { it!!.contains("--greeting \"$textValue\" \"$destination\"") }
        )
    }
}
