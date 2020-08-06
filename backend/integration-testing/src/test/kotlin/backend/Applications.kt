package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.RetrieveBalanceRequest
import dk.sdu.cloud.accounting.api.WalletOwnerType
import dk.sdu.cloud.accounting.api.Wallets
import dk.sdu.cloud.app.orchestrator.AppOrchestratorService
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobWithStatus
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withProject
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatProperty
import io.ktor.http.HttpStatusCode
import io.ktor.util.toByteArray
import kotlinx.coroutines.io.ByteReadChannel
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

object SampleApplications {
    val figlet = NameAndVersion("figlet", "1.0.0")
    val longRunning = NameAndVersion("long-running", "1.0.0")

    fun figletParams(text: String): Map<String, Any> = mapOf("text" to text)

    suspend fun create() {
        ToolStore.create.call(
            BinaryStream.outgoingFromText(
                //language=yaml
                """
                    ---
                    tool: v1

                    title: Figlet

                    name: figlet
                    version: 1.0.0

                    container: truek/figlets:1.1.1

                    authors:
                    - Dan Sebastian Thrane <dthrane@imada.sdu.dk>

                    description: Tool for rendering text.

                    defaultTimeAllocation:
                      hours: 0
                      minutes: 1
                      seconds: 0

                    backend: DOCKER
                """.trimIndent()
            ),
            serviceClient
        ).orThrow()

        AppStore.create.call(
            BinaryStream.outgoingFromText(
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
            ),
            serviceClient
        ).orThrow()

        AppStore.create.call(
            BinaryStream.outgoingFromText(
                //language=yaml
                """
                    ---
                    application: v1
                    
                    title: long running
                    name: long-running
                    version: 1.0.0
                    
                    tool:
                      name: figlet
                      version: 1.0.0
                    
                    authors: ["Dan Thrane"]
                    
                    description: Runs for a long time
                    
                    # We just count to a really big number
                    invocation:
                    - figlet-count
                    - 1000000000
                """.trimIndent()
            ),
            serviceClient
        ).orThrow()
    }
}

class ApplicationTest : IntegrationTest() {
    @Test
    fun `test figlet`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()

        val user = createUser()
        val rootProject = initializeRootProject()
        initializeAllPersonalFunds(user.username, rootProject)

        val jobId = JobDescriptions.start.call(
            StartJobRequest(
                SampleApplications.figlet,
                parameters = SampleApplications.figletParams("Hello, World!"),
                reservation = sampleCompute.id
            ),
            user.client
        ).orThrow().jobId

        val status: JobWithStatus = waitForJob(jobId, user.client)

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

    private suspend fun waitForJob(
        jobId: String,
        userClient: AuthenticatedClient
    ): JobWithStatus {
        lateinit var status: JobWithStatus
        retrySection(attempts = 300, delay = 10_000) {
            status = JobDescriptions.findById.call(FindByStringId(jobId), userClient).orThrow()
            require(status.state.isFinal()) { "Current job state is: ${status.state}" }
            require(status.outputFolder != null)
        }
        return status
    }

    @Test
    fun `test accounting with job timeout`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val user = createUser()
        val rootProject = initializeRootProject()
        initializeAllPersonalFunds(user.username, rootProject)

        val wbBefore = findPersonalWallet(user.username, user.client, sampleCompute.category)!!

        val jobId = JobDescriptions.start.call(
            StartJobRequest(
                SampleApplications.longRunning,
                parameters = emptyMap(),
                reservation = sampleCompute.id,
                maxTime = SimpleDuration(0, 0, 30)
            ),
            user.client
        ).orThrow().jobId

        waitForJob(jobId, user.client)

        val wbAfter = findPersonalWallet(user.username, user.client, sampleCompute.category)!!

        assertEquals(wbBefore.balance - sampleCompute.pricePerUnit, wbAfter.balance)
    }

    @Test
    fun `test accounting in project`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()

        val project = initializeNormalProject(rootProject)
        setProjectQuota(project.projectId, 10.GiB)

        val wbBefore = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        val jobId = JobDescriptions.start.call(
            StartJobRequest(
                SampleApplications.figlet,
                parameters = SampleApplications.figletParams("Hello"),
                reservation = sampleCompute.id
            ),
            project.piClient.withProject(project.projectId)
        ).orThrow().jobId

        val finalJob = waitForJob(jobId, project.piClient.withProject(project.projectId))

        val wbAfter = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        assertEquals(wbBefore.balance - sampleCompute.pricePerUnit, wbAfter.balance)

        assertThatInstance(finalJob, "has a correct output folder") { resp ->
            val outputFolder = resp.outputFolder
            outputFolder != null && outputFolder.startsWith(
                joinPath(
                    projectHomeDirectory(project.projectId),
                    PERSONAL_REPOSITORY,
                    project.piUsername,
                    isDirectory = true
                )
            )
        }
    }

    @Test
    fun `test accounting in project (user of project)`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()

        val project = initializeNormalProject(rootProject)
        setProjectQuota(project.projectId, 10.GiB)

        val user = createUser()
        addMemberToProject(project.projectId, project.piClient, user.client, user.username)

        val wbBefore = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        val jobId = JobDescriptions.start.call(
            StartJobRequest(
                SampleApplications.figlet,
                parameters = SampleApplications.figletParams("Hello"),
                reservation = sampleCompute.id
            ),
            user.client.withProject(project.projectId)
        ).orThrow().jobId

        val finalJob = waitForJob(jobId, user.client.withProject(project.projectId))

        val wbAfter = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        assertEquals(wbBefore.balance - sampleCompute.pricePerUnit, wbAfter.balance)

        assertThatInstance(finalJob, "has a correct output folder") { resp ->
            val outputFolder = resp.outputFolder
            outputFolder != null && outputFolder.startsWith(
                joinPath(
                    projectHomeDirectory(project.projectId),
                    PERSONAL_REPOSITORY,
                    user.username,
                    isDirectory = true
                )
            )
        }
    }

    @Test
    fun `test accounting when no resources`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val user = createUser()
        setPersonalQuota(rootProject, user.username, 10.GiB) // Set a quota but don't add funds

        assertThatInstance(
            JobDescriptions.start.call(
                StartJobRequest(
                    SampleApplications.figlet,
                    parameters = SampleApplications.figletParams("Hello"),
                    reservation = sampleCompute.id
                ),
                user.client
            ),
            "fails with payment required"
        ) { it.statusCode == HttpStatusCode.PaymentRequired }
    }

    @Test
    fun `test accounting when no remaining quota`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val user = createUser()
        addFundsToPersonalProject(rootProject, user.username, sampleCompute.category, 10_000.DKK)
        setPersonalQuota(rootProject, user.username, 0.GiB) // add funds but no quota

        assertThatInstance(
            JobDescriptions.start.call(
                StartJobRequest(
                    SampleApplications.figlet,
                    parameters = SampleApplications.figletParams("Hello"),
                    reservation = sampleCompute.id
                ),
                user.client
            ),
            "fails with payment required"
        ) { it.statusCode == HttpStatusCode.PaymentRequired }
    }
}
