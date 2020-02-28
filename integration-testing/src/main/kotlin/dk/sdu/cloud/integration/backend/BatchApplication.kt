package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobWithStatus
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.service.Loggable
import io.ktor.util.toByteArray

class BatchApplication(private val userA: UserAndClient) {
    suspend fun runTest() {
        log.info("Starting Figlet...")
        val jobId = JobDescriptions.start.call(
            StartJobRequest(
                NameAndVersion("figlet", "1.0.3"),
                name = "figlet",
                parameters = mapOf(
                    "text" to "Hello, World!"
                )
            ),
            userA.client
        ).orThrow().jobId

        log.info("Application was started: $jobId")
        lateinit var status: JobWithStatus
        retrySection(attempts = 300, delay = 10_000) {
            log.info("Checking job status")
            status = JobDescriptions.findById.call(FindByStringId(jobId), userA.client).orThrow()
            require(status.state == JobState.SUCCESS) { "Current job state is: ${status.state}" }
            require(status.outputFolder != null)
        }

        val outputFolder = status.outputFolder!!
        log.info("Output folder is: $outputFolder")
        retrySection {
            log.info("Downloading stdout.txt")
            val stdout = FileDescriptions.download
                .call(
                    DownloadByURI(
                        path = joinPath(outputFolder, "stdout.txt"),
                        token = null
                    ),
                    userA.client
                )
                .orThrow()
                .asIngoing()
                .channel
                .toByteArray()
                .toString(Charsets.UTF_8)

            require(stdout.lines().size == 7) { "$stdout\nOutput does not appear to be correct" }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
