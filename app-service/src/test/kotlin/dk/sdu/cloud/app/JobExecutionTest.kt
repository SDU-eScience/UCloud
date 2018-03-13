package dk.sdu.cloud.app

import com.auth0.jwt.JWT
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

class JobExecutionTest {
    val irodsConfiguration: StorageConfiguration = StorageConfiguration("irods", 1247, "tempZone")
    val sshUser = "nobody"
    val sBatchGenerator = SBatchGenerator()

    @RelaxedMockK
    lateinit var cloud: RefreshingJWTAuthenticator

    @RelaxedMockK
    lateinit var producer: MappedEventProducer<String, AppEvent>

    @RelaxedMockK
    lateinit var irods: StorageConnectionFactory

    @RelaxedMockK
    lateinit var irodsConnection: StorageConnection

    @RelaxedMockK
    lateinit var jobsDao: JobsDAO

    @RelaxedMockK
    lateinit var slurmPollAgent: SlurmPollAgent

    @RelaxedMockK
    lateinit var sshPool: SSHConnectionPool

    lateinit var service: JobExecutionService

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        every { irods.createForAccount(any(), any()) } answers { Ok(irodsConnection) }
        every { jobsDao.transaction<Any>(captureLambda()) } answers {
            lambda<JobsDAO.() -> Any>().invoke(jobsDao)
        }

        service = JobExecutionService(
            cloud,
            producer,
            irods,
            irodsConfiguration,
            sBatchGenerator,
            jobsDao,
            slurmPollAgent,
            sshPool,
            sshUser
        )

        val tools = listOf(dummyTool)
        tools.forEach { createTemporaryTool(it) }

        val applications = listOf(
            noParamsApplication,
            singleMandatoryParamApplication
        )
        applications.forEach { createTemporaryApplication(it) }
    }

    @Test
    fun testValidationOfSimpleJob() {
        // Run test
        val result = service.startJob(AppRequest.Start(noParamsApplication.info, emptyMap()), dummyToken)

        // Check that everything is okay
        assertNotEquals("", result)

        verify { jobsDao.createJob(result, dummyTokenSubject, noParamsApplication) }

        coVerify {
            producer.emit(
                match {
                    it is AppEvent.Validated &&
                            it.systemId == result &&
                            it.owner == dummyTokenSubject &&
                            it.jwt == dummyToken.token
                }
            )
        }
    }

    @Test(expected = JobValidationException::class)
    fun testValidationOfMissingOptionalParameter() {
        service.startJob(AppRequest.Start(singleMandatoryParamApplication.info, emptyMap()), dummyToken)
    }

    // ============================================================
    // ====================== Test resources ======================
    // ============================================================

    private val dummyTokenSubject = "test"
    private val dummyToken = JWT.decode(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJ0ZXN0IiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
                "GxfHPZdY5aBZRt2g-ogPn6LfaG7MnAag-psqzquZKw8"
    )

    private val dummyTool = ToolDescription(
        info = NameAndVersion("dummy", "1.0.0"),
        container = "dummy.simg",
        defaultNumberOfNodes = 1,
        defaultTasksPerNode = 1,
        defaultMaxTime = SimpleDuration(1, 0, 0),
        requiredModules = emptyList(),
        authors = listOf("Author"),
        prettyName = "Dummy",
        createdAt = System.currentTimeMillis(),
        modifiedAt = System.currentTimeMillis(),
        description = "Dummy description"
    )

    private val noParamsApplication = ApplicationDescription(
        tool = dummyTool.info,
        info = NameAndVersion("noparams", "1.0.0"),
        authors = listOf("Author"),
        prettyName = "noparms",
        createdAt = System.currentTimeMillis(),
        modifiedAt = System.currentTimeMillis(),
        description = "noparms",
        invocation = listOf(WordInvocationParameter("noparms")),
        parameters = emptyList(),
        outputFileGlobs = emptyList()
    )

    private val singleMandatoryParamApplication = ApplicationDescription(
        tool = dummyTool.info,
        info = NameAndVersion("singlearg", "1.0.0"),
        authors = listOf("Author"),
        prettyName = "singlearg",
        createdAt = System.currentTimeMillis(),
        modifiedAt = System.currentTimeMillis(),
        description = "singlearg",
        invocation = listOf(WordInvocationParameter("singlearg"), VariableInvocationParameter(listOf("arg"))),
        parameters = listOf(ApplicationParameter.Text("arg", false)),
        outputFileGlobs = emptyList()
    )

    private fun createTemporaryApplication(application: ApplicationDescription) {
        ApplicationDAO.inMemoryDB[application.info.name] = listOf(application)
    }

    private fun createTemporaryTool(tool: ToolDescription) {
        ToolDAO.inMemoryDB[tool.info.name] = listOf(tool)
    }
}