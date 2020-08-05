package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.AppOrchestratorService
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobWithStatus
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.DownloadByURI
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import io.ktor.util.toByteArray
import kotlinx.coroutines.io.ByteReadChannel
import org.junit.Ignore
import org.junit.Test

object SampleApplications {
    val figlet = NameAndVersion("figlet", "1.0.0")
    val httpbin = NameAndVersion("httpbin", "1.0.0")

    suspend fun create() {
        ToolStore.create.call(
            BinaryStream.outgoingFromChannel(ByteReadChannel(
                //language=yaml
                """
                    ---
                    tool: v1

                    title: Figlet

                    name: figlet
                    version: 1.0.0

                    container: truek/figlets:1.1.0

                    authors:
                    - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                    description: Tool for rendering text.

                    defaultTimeAllocation:
                      hours: 0
                      minutes: 1
                      seconds: 0

                    backend: DOCKER
                """.trimIndent()
            )),
            serviceClient
        ).orThrow()

        AppStore.create.call(
            BinaryStream.outgoingFromChannel(ByteReadChannel(
                //language=yaml
                """
                   ---
                   application: v1

                   title: Figlet
                   name: figlet
                   version: 1.0.0

                   tool:
                     name: figlet
                     version: 1.0.0

                   authors:
                   - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                   description: >
                     Render some text with Figlet Docker!

                   invocation:
                   - figlet
                   - type: var
                     vars: text
                     
                   parameters:
                     text:
                       title: "Some text to render with figlet"
                       type: text
     
                """.trimIndent()
            )),
            serviceClient
        ).orThrow()

        ToolStore.create.call(
            BinaryStream.outgoingFromChannel(ByteReadChannel(
                //language=yaml
                """
                    ---
                    tool: v1

                    title: HttpBin

                    name: httpbin
                    version: 1.0.0

                    container: kennethreitz/httpbin

                    authors:
                    - Kenneth Reitz

                    description: A tool for testing httprequests.

                    defaultTimeAllocation:
                      hours: 1
                      minutes: 0
                      seconds: 0

                    backend: DOCKER
                """.trimIndent()
            )),
            serviceClient
        ).orThrow()

        AppStore.create.call(
            BinaryStream.outgoingFromChannel(ByteReadChannel(
                //language=yaml
                """
                   ---
                   application: v1

                   title: HttpBin
                   name: HttpBin
                   version: 1.0.0

                   tool:
                     name: httpbin
                     version: 1.0.0

                   authors:
                   - Kenneth Reitz

                   description:
                     Test Http requests

                   invocation:
                     - "gunicorn"
                     - "-b"
                     - "0.0.0.0:80"
                     - "httpbin:app"
                     - "-k"
                     - "gevent"

                   applicationType: WEB

                   web:
                     port: 80 
                """.trimIndent()
            )),
            serviceClient
        ).orThrow()
    }
}

class ApplicationTest : IntegrationTest() {
    @Test
    @Ignore
    fun `test figlet`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()

        val user = createUser()
        addFundsToPersonalProject(initializeRootProject(), user.username)

        val jobId = JobDescriptions.start.call(
            StartJobRequest(
                SampleApplications.figlet,
                parameters = mapOf(
                    "text" to "Hello, World!"
                ),
                reservation = sampleCompute.id
            ),
            serviceClient
        ).orThrow().jobId

        lateinit var status: JobWithStatus
        retrySection(attempts = 300, delay = 10_000) {
            status = JobDescriptions.findById.call(FindByStringId(jobId), user.client).orThrow()
            require(status.state == JobState.SUCCESS) { "Current job state is: ${status.state}" }
            require(status.outputFolder != null)
        }

        val outputFolder = status.outputFolder!!
        retrySection {
            val stdout = FileDescriptions.download
                .call(
                    DownloadByURI(
                        path = joinPath(outputFolder, "stdout.txt"),
                        token = null
                    ),
                    user.client
                )
                .orThrow()
                .asIngoing()
                .channel
                .toByteArray()
                .toString(Charsets.UTF_8)

            require(stdout.lines().size == 7) { "$stdout\nOutput does not appear to be correct" }
        }
    }
}
