package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.grant.api.DKK
import dk.sdu.cloud.integration.IntegrationTest
import dk.sdu.cloud.integration.UCloudLauncher
import dk.sdu.cloud.integration.UCloudLauncher.serviceClient
import dk.sdu.cloud.integration.retrySection
import dk.sdu.cloud.integration.t
import dk.sdu.cloud.service.test.assertThatInstance
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import org.junit.Test
import kotlin.test.assertEquals

object SampleApplications {
    val figlet = NameAndVersion("figlet", "1.0.0")
    val longRunning = NameAndVersion("long-running", "1.0.0")

    fun figletParams(text: String): Map<String, AppParameterValue> = mapOf("text" to AppParameterValue.Text(text))

    suspend fun create() {
        ToolStore.create.call(
            Unit,
            serviceClient.withHttpBody(
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
                """.trimIndent(),
                ContentType("text", "yaml")
            )
        ).orThrow()

        AppStore.create.call(
            Unit,
            serviceClient.withHttpBody(
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
     
                """.trimIndent(),
                ContentType("text", "yaml")
            )
        ).orThrow()

        AppStore.create.call(
            Unit,
            serviceClient.withHttpBody(
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
                """.trimIndent(),
                ContentType("text", "yaml")
            )
        ).orThrow()
    }
}

fun Product.Compute.reference(): ComputeProductReference = ComputeProductReference(id, category.id, category.provider)

class ApplicationTest : IntegrationTest() {
    @Test
    fun `test figlet`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()

        val user = createUser()
        val rootProject = initializeRootProject()
        initializeAllPersonalFunds(user.username, rootProject)

        val jobId = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello, World"),
                    resources = emptyList(),
                )
            ),
            user.client
        ).orThrow().ids.single()

        val status: Job = waitForJob(jobId, user.client)

        val outputFolder = status.output!!.outputFolder
        /*
        retrySection {
            val stdout = FileDescriptions.download
                .call(
                    DownloadByURI(
                        path = joinPath(outputFolder, "stdout.txt"),
                        token = null
                    ),
                    user.client
                )
                .ctx
                .let { it as OutgoingHttpCall }
                .response!!
                .receive<ByteReadChannel>()
                .toByteArray()
                .toString(Charsets.UTF_8)

            require(stdout.lines().size == 7) { "$stdout\nOutput does not appear to be correct" }
        }
         */
    }

    private suspend fun waitForJob(
        jobId: String,
        userClient: AuthenticatedClient,
    ): Job {
        lateinit var status: Job
        retrySection(attempts = 300, delay = 10_000) {
            status = Jobs.retrieve.call(JobsRetrieveRequest(jobId), userClient).orThrow()
            require(status.status.state.isFinal()) { "Current job state is: ${status.status.state}" }
            require(status.output != null) { "output must be non-null" }
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

        val jobId = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.longRunning,
                    sampleCompute.reference(),
                    parameters = emptyMap(),
                    resources = emptyList(),
                    timeAllocation = SimpleDuration(0, 0, 30),
                )
            ),
            user.client
        ).orThrow().ids.single()

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
        // setProjectQuota(project.projectId, 10.GiB)

        val wbBefore = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        val jobId = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            project.piClient.withProject(project.projectId)
        ).orThrow().ids.single()

        val finalJob = waitForJob(jobId, project.piClient.withProject(project.projectId))

        val wbAfter = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        assertEquals(wbBefore.balance - sampleCompute.pricePerUnit, wbAfter.balance)

        /*
        assertThatInstance(finalJob, "has a correct output folder") { resp ->
            val outputFolder = resp.output!!.outputFolder
            outputFolder != null && outputFolder.startsWith(
                joinPath(
                    projectHomeDirectory(project.projectId),
                    PERSONAL_REPOSITORY,
                    project.piUsername,
                    isDirectory = true
                )
            )
        }
         */
    }

    @Test
    fun `test accounting in project (user of project)`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()

        val project = initializeNormalProject(rootProject)
        // setProjectQuota(project.projectId, 10.GiB)

        val user = createUser()
        addMemberToProject(project.projectId, project.piClient, user.client, user.username)

        val wbBefore = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        val jobId = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            user.client.withProject(project.projectId)
        ).orThrow().ids.single()

        val finalJob = waitForJob(jobId, user.client.withProject(project.projectId))

        val wbAfter = findProjectWallet(project.projectId, project.piClient, sampleCompute.category)
            ?: error("Could not find wallet")

        assertEquals(wbBefore.balance - sampleCompute.pricePerUnit, wbAfter.balance)

        /*
        assertThatInstance(finalJob, "has a correct output folder") { resp ->
            val outputFolder = resp.output!!.outputFolder
            outputFolder.startsWith(
                joinPath(
                    projectHomeDirectory(project.projectId),
                    PERSONAL_REPOSITORY,
                    user.username,
                    isDirectory = true
                )
            )
        }
         */
    }

    @Test
    fun `test accounting when no resources`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val user = createUser()
        // setPersonalQuota(rootProject, user.username, 10.GiB) // Set a quota but don't add funds

        assertThatInstance(
            Jobs.create.call(
                bulkRequestOf(
                    JobSpecification(
                        SampleApplications.figlet,
                        sampleCompute.reference(),
                        parameters = SampleApplications.figletParams("Hello"),
                        resources = emptyList(),
                    )
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
        // setPersonalQuota(rootProject, user.username, 0.GiB) // add funds but no quota

        assertThatInstance(
            Jobs.create.call(
                bulkRequestOf(
                    JobSpecification(
                        SampleApplications.figlet,
                        sampleCompute.reference(),
                        parameters = SampleApplications.figletParams("Hello"),
                        resources = emptyList(),
                    )
                ),
                user.client
            ),
            "fails with payment required"
        ) { it.statusCode == HttpStatusCode.PaymentRequired }
    }

    @Test
    fun `test cancelling job`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()

        val user = createUser()
        val rootProject = initializeRootProject()
        initializeAllPersonalFunds(user.username, rootProject)

        addFundsToPersonalProject(rootProject, user.username, sampleCompute.category, 10_000.DKK)
        // setPersonalQuota(rootProject, user.username, 10.GiB)
        val jobId = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            user.client
        ).orThrow().ids.single()

        assert(Jobs.browse.call(JobsBrowseRequest(10), user.client).orThrow().items.isNotEmpty())
        Jobs.delete.call(bulkRequestOf(FindByStringId(jobId)), user.client)
        waitForJob(jobId, user.client)
        val result = Jobs.browse.call(JobsBrowseRequest(10), user.client).orThrow().items
        assert(result.isNotEmpty() && result.first().status.state == JobState.FAILURE)
    }

    @Test
    fun `test job by project not visible in users personal workspace`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val project = initializeNormalProject(rootProject)
        // setProjectQuota(project.projectId, 10.GiB)

        Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            project.piClient.withProject(project.projectId)
        ).orThrow().ids.single()

        assert(Jobs.browse.call(JobsBrowseRequest(10), project.piClient.withProject(project.projectId))
            .orThrow().items.isNotEmpty())
        assert(Jobs.browse.call(JobsBrowseRequest(10), project.piClient).orThrow().items.isEmpty())
    }

    @Test
    fun `test job by user not visible by project they are part of`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val user = createUser()

        initializeAllPersonalFunds(user.username, rootProject)

        Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            user.client
        ).orThrow().ids.single()

        assert(Jobs.browse.call(JobsBrowseRequest(10), user.client.withProject(rootProject)).orThrow().items.isEmpty())
        assert(Jobs.browse.call(JobsBrowseRequest(10), user.client).orThrow().items.isNotEmpty())
    }

    @Test
    fun `test job by project not cancelable by non-associated user`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val project = initializeNormalProject(rootProject)
        val userB = createUser()
        // setProjectQuota(project.projectId, 10.GiB)

        val id = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            project.piClient.withProject(project.projectId)
        ).orThrow().ids.single()

        val status = Jobs.delete.call(bulkRequestOf(FindByStringId(id)), userB.client)
        assert(!status.statusCode.isSuccess())
    }

    @Test
    fun `test job by user not cancelable by non-associated project`() = t {
        UCloudLauncher.requireK8s()
        SampleApplications.create()
        val rootProject = initializeRootProject()
        val project = initializeNormalProject(rootProject)
        val user = createUser()

        initializeAllPersonalFunds(user.username, rootProject)

        val jobId = Jobs.create.call(
            bulkRequestOf(
                JobSpecification(
                    SampleApplications.figlet,
                    sampleCompute.reference(),
                    parameters = SampleApplications.figletParams("Hello"),
                    resources = emptyList(),
                )
            ),
            user.client
        ).orThrow().ids.single()

        assert(Jobs.browse.call(JobsBrowseRequest(10), user.client).orThrow().items.isNotEmpty())

        val result = Jobs.delete.call(bulkRequestOf(FindByStringId(jobId)), project.piClient)
        assert(!result.statusCode.isSuccess())

        assert(Jobs.browse.call(JobsBrowseRequest(10), user.client).orThrow().items.isNotEmpty())
    }
}
