package dk.sdu.cloud.app

import com.auth0.jwt.JWT
import com.jcraft.jsch.JSchException
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.app.services.ssh.SSHConnection
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.scpUpload
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.ext.*
import dk.sdu.cloud.storage.model.FileStat
import dk.sdu.cloud.storage.model.StoragePath
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import java.util.*

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

    @RelaxedMockK
    lateinit var sshConnection: SSHConnection

    @RelaxedMockK
    lateinit var fileQuery: FileQueryOperations

    @RelaxedMockK
    lateinit var files: FileOperations

    @RelaxedMockK
    lateinit var paths: PathOperations

    lateinit var service: JobExecutionService

    val emitSlot = slot<AppEvent>()

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        coEvery { producer.emit(capture(emitSlot)) } just Runs

        every { jobsDao.transaction<Any>(captureLambda()) } answers {
            lambda<JobsDAO.() -> Any>().invoke(jobsDao)
        }

        every { sshPool.borrowConnection() } answers {
            Pair(0, sshConnection)
        }

        every { irods.createForAccount(any(), any()) } answers { Ok(irodsConnection) }
        every { irodsConnection.fileQuery } returns fileQuery
        every { irodsConnection.files } returns files
        every { irodsConnection.paths } returns paths
        every { paths.parseAbsolute(any(), any()) } answers { StoragePath(call.invocation.args.first() as String) }

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

        val applications = listOf(noParamsApplication)
        applications.forEach { createTemporaryApplication(it) }
    }

    @Test
    fun testValidationOfSimpleJob() {
        val result = service.startJob(AppRequest.Start(noParamsApplication.info, emptyMap()), dummyToken)
        verifyJobStarted(result, noParamsApplication)
    }

    @Test(expected = JobValidationException::class)
    fun testValidationOfMissingOptionalParameter() {
        val application = app(
            "singlearg",
            invocation = listOf(WordInvocationParameter("singlearg"), VariableInvocationParameter(listOf("arg"))),
            parameters = listOf(ApplicationParameter.Text("arg", false))
        )
        createTemporaryApplication(application)

        val result = service.startJob(AppRequest.Start(application.info, emptyMap()), dummyToken)
        verifyJobStarted(result, application)
    }

    @Test
    fun testValidationOfOptionalNoDefault() {
        val application = app(
            "singleoptional",
            invocation = listOf(WordInvocationParameter("eh"), VariableInvocationParameter(listOf("arg"))),
            parameters = listOf(ApplicationParameter.Text("arg", true, defaultValue = null))
        )

        createTemporaryApplication(application)

        val result = service.startJob(AppRequest.Start(application.info, emptyMap()), dummyToken)
        verifyJobStarted(result, application)
    }

    @Test
    fun testValidationOfOptionalWithDefault() {
        val application = app(
            "singleoptionaldefault",
            invocation = listOf(WordInvocationParameter("eh"), VariableInvocationParameter(listOf("arg"))),
            parameters = listOf(ApplicationParameter.Text("arg", true, defaultValue = "foobar"))
        )
        createTemporaryApplication(application)

        val result = service.startJob(AppRequest.Start(application.info, emptyMap()), dummyToken)
        verifyJobStarted(result, application)
    }

    @Test
    fun testMultipleVariables() {
        val application = app(
            "multiple",
            invocation = listOf(WordInvocationParameter("eh"), VariableInvocationParameter(listOf("arg", "arg2"))),
            parameters = listOf(
                ApplicationParameter.Text("arg", true, defaultValue = "foobar"),
                ApplicationParameter.Text("arg2", false, defaultValue = "foobar")
            )
        )
        createTemporaryApplication(application)

        val result = service.startJob(
            AppRequest.Start(
                application.info,
                mapOf(
                    "arg" to "foo",
                    "arg2" to "bar"
                )
            ),
            dummyToken
        )
        verifyJobStarted(result, application)
    }

    @Test(expected = JobValidationException::class)
    fun testMultipleVariablesInvalid() {
        val application = app(
            "multiple",
            invocation = listOf(WordInvocationParameter("eh"), VariableInvocationParameter(listOf("arg", "arg2"))),
            parameters = listOf(
                ApplicationParameter.Text("arg", true, defaultValue = "foobar"),
                ApplicationParameter.Text("arg2", false, defaultValue = "foobar")
            )
        )
        createTemporaryApplication(application)

        val result = service.startJob(
            AppRequest.Start(
                application.info,
                mapOf(
                    "arg" to "foo"
                )
            ),
            dummyToken
        )
        verifyJobStarted(result, application)
    }

    @Test
    fun testFileInputValid() {
        val path = "/home/foo/Uploads/1.txt"
        every { fileQuery.stat(match { it.path == path }) } answers {
            Ok(
                FileStat(
                    call.invocation.args.first() as StoragePath,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    dummyTokenSubject,
                    10L,
                    ""
                )
            )
        }

        val application = app(
            "files",
            invocation = listOf(VariableInvocationParameter(listOf("myFile"))),
            parameters = listOf(
                ApplicationParameter.InputFile("myFile", false)
            )
        )
        createTemporaryApplication(application)

        val result = service.startJob(
            AppRequest.Start(
                application.info,
                mapOf(
                    "myFile" to mapOf("source" to path, "destination" to "1.txt")
                )
            ),
            dummyToken
        )

        verifyJobStarted(result, application)
        val captured = emitSlot.captured as AppEvent.Validated
        val workDir = URI(captured.workingDirectory)

        assertEquals(1, captured.files.size)

        val file = captured.files.first()
        assertEquals(10L, file.stat.sizeInBytes)
        assertEquals(path, file.sourcePath.path)
        assertEquals(workDir.resolve("1.txt").path, file.destinationPath)
        assertEquals("1.txt", file.destinationFileName)
    }

    @Test(expected = JobValidationException::class)
    fun testFileInputValidationWithMissingFile() {
        val path = "/home/foo/Uploads/1.txt"
        every { fileQuery.stat(match { it.path == path }) } answers {
            Error.notFound()
        }

        val application = app(
            "files",
            invocation = listOf(VariableInvocationParameter(listOf("myFile"))),
            parameters = listOf(
                ApplicationParameter.InputFile("myFile", false)
            )
        )
        createTemporaryApplication(application)

        service.startJob(
            AppRequest.Start(
                application.info,
                mapOf(
                    "myFile" to mapOf("source" to path, "destination" to "1.txt")
                )
            ),
            dummyToken
        )
    }

    @Test
    fun testValidFileInputValidationWithMultipleFiles() {
        val paths = listOf("/home/foo/Uploads/1.txt", "/home/foo/foo.png")
        paths.forEach { path ->
            every { fileQuery.stat(match { it.path == path }) } answers {
                Ok(
                    FileStat(
                        call.invocation.args.first() as StoragePath,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        dummyTokenSubject,
                        10L,
                        ""
                    )
                )
            }
        }

        val application = app(
            "files",
            invocation = listOf(VariableInvocationParameter(listOf("myFile"))),
            parameters = listOf(
                ApplicationParameter.InputFile("myFile", false),
                ApplicationParameter.InputFile("myFile2", false)
            )
        )
        createTemporaryApplication(application)

        val result = service.startJob(
            AppRequest.Start(
                application.info,
                mapOf(
                    "myFile" to mapOf("source" to paths[0], "destination" to "1.txt"),
                    "myFile2" to mapOf("source" to paths[1], "destination" to "foo.png")
                )
            ),
            dummyToken
        )

        verifyJobStarted(result, application)
        val captured = emitSlot.captured as AppEvent.Validated
        val workDir = URI(captured.workingDirectory)

        assertEquals(paths.size, captured.files.size)

        paths.forEachIndexed { idx, path ->
            val file = captured.files[idx]
            val name = path.substringAfterLast('/')
            assertEquals(10L, file.stat.sizeInBytes)
            assertEquals(path, file.sourcePath.path)
            assertEquals(workDir.resolve(name).path, file.destinationPath)
            assertEquals(name, file.destinationFileName)
        }
    }

    @Test(expected = JobValidationException::class)
    fun testInvalidFileInputValidationWithMultipleFiles() {
        val paths = listOf("/home/foo/Uploads/1.txt", "/home/foo/foo.png")
        paths.forEach { path ->
            every { fileQuery.stat(match { it.path == path }) } answers {
                Ok(
                    FileStat(
                        call.invocation.args.first() as StoragePath,
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        dummyTokenSubject,
                        10L,
                        ""
                    )
                )
            }
        }

        val application = app(
            "files",
            invocation = listOf(VariableInvocationParameter(listOf("myFile"))),
            parameters = listOf(
                ApplicationParameter.InputFile("myFile", false),
                ApplicationParameter.InputFile("myFile2", false)
            )
        )
        createTemporaryApplication(application)

        val result = service.startJob(
            AppRequest.Start(
                application.info,
                mapOf(
                    "myFile" to mapOf("source" to paths[0], "destination" to "1.txt")
                )
            ),
            dummyToken
        )

        verifyJobStarted(result, application)
        val captured = emitSlot.captured as AppEvent.Validated
        val workDir = URI(captured.workingDirectory)

        assertEquals(paths.size, captured.files.size)

        paths.forEachIndexed { idx, path ->
            val file = captured.files[idx]
            val name = path.substringAfterLast('/')
            assertEquals(10L, file.stat.sizeInBytes)
            assertEquals(path, file.sourcePath.path)
            assertEquals(workDir.resolve(name).path, file.destinationPath)
            assertEquals(name, file.destinationFileName)
        }
    }

    private fun verifyJobStarted(result: String, app: ApplicationDescription) {
        assertNotEquals("", result)

        verify { jobsDao.createJob(result, dummyTokenSubject, app) }

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

    @Test
    fun testJobPreparationBadJWT() {
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
            "/scratch/sduescience/p",
            "/scratch/sduescience/p/files",
            emptyList(),
            "job"
        )

        // We don't mock the TokenValidation part, thus the token will not validate (bad signature)
        // We just check that we actually output the correct event
        service.handleAppEvent(event)

        assertTrue(emitSlot.isCaptured)
        val outputEvent = emitSlot.captured as AppEvent.Completed
        assertFalse(outputEvent.successful)
    }

    @Test
    fun testJobPreparationNoFiles() {
        val inlineSBatchJob = "job"
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
            "/scratch/sduescience/p",
            "/scratch/sduescience/p/files",
            emptyList(),
            inlineSBatchJob
        )

        val (fileNameSlot, fileContents) = withMockedAuthentication {
            withMockedSCPUpload {
                service.handleAppEvent(event)
            }
        }

        assertTrue(emitSlot.isCaptured)
        emitSlot.captured as AppEvent.Prepared

        assertEquals(1, fileNameSlot.size)
        assertEquals(1, fileContents.size)

        assertEquals("job.sh", fileNameSlot.first())
        assertEquals(inlineSBatchJob, String(fileContents.first()))
    }

    @Test
    fun testJobPreparationWithFiles() {
        val application = app(
            "singlefile",
            listOf(VariableInvocationParameter(listOf("myFile"))),
            listOf(ApplicationParameter.InputFile("myFile", false))
        )
        createTemporaryApplication(application)

        val writerSlot = slot<OutputStream>()
        val fileName = "file.txt"
        every { files.get(match { it.name == fileName }, capture(writerSlot)) } answers {
            writerSlot.captured.write(fileName.toByteArray())
        }

        val inlineSBatchJob = "job"
        val workingDirectory = "/scratch/sduescience/p/files"
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(application, dummyTool),
            "/scratch/sduescience/p",
            workingDirectory,
            listOf(
                ValidatedFileForUpload(
                    irodsStat(fileName),
                    fileName,
                    "$workingDirectory/$fileName",
                    StoragePath(fileName)
                )
            ),
            inlineSBatchJob
        )

        val (fileNameSlot, fileContents) = withMockedAuthentication {
            withMockedSCPUpload { service.handleAppEvent(event) }
        }

        assertTrue(emitSlot.isCaptured)
        emitSlot.captured as AppEvent.Prepared

        assertEquals(2, fileNameSlot.size)
        assertEquals(2, fileContents.size)

        run {
            // Check job file
            val jobSlot = fileNameSlot.indexOfFirst { it == "job.sh" }
            assertNotEquals(-1, jobSlot)
            assertEquals(inlineSBatchJob, String(fileContents[jobSlot]))
        }

        run {
            // Check input file
            val inputFileSlot = fileNameSlot.indexOfFirst { it == fileName }
            assertNotEquals(-1, inputFileSlot)
            assertEquals(fileName, String(fileContents[inputFileSlot]))
        }
    }

    @Test
    fun testJobPreparationWithFilesWithIRodsFailure() {
        val application = app(
            "singlefile",
            listOf(VariableInvocationParameter(listOf("myFile"))),
            listOf(ApplicationParameter.InputFile("myFile", false))
        )
        createTemporaryApplication(application)

        val writerSlot = slot<OutputStream>()
        val fileName = "file.txt"
        every { files.get(match { it.name == fileName }, capture(writerSlot)) } throws StorageException("Bad failure")

        val inlineSBatchJob = "job"
        val workingDirectory = "/scratch/sduescience/p/files"
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(application, dummyTool),
            "/scratch/sduescience/p",
            workingDirectory,
            listOf(
                ValidatedFileForUpload(
                    irodsStat(fileName),
                    fileName,
                    "$workingDirectory/$fileName",
                    StoragePath(fileName)
                )
            ),
            inlineSBatchJob
        )

        withMockedAuthentication {
            withMockedSCPUpload { service.handleAppEvent(event) }
        }

        assertTrue(emitSlot.isCaptured)
        val outputEvent = emitSlot.captured as AppEvent.Completed
        assertFalse(outputEvent.successful)
    }

    @Test
    fun testJobPreparationWithFilesWithUploadFailure() {
        val application = app(
            "singlefile",
            listOf(VariableInvocationParameter(listOf("myFile"))),
            listOf(ApplicationParameter.InputFile("myFile", false))
        )
        createTemporaryApplication(application)

        val writerSlot = slot<OutputStream>()
        val fileName = "file.txt"
        every { files.get(match { it.name == fileName }, capture(writerSlot)) } answers {
            writerSlot.captured.write(fileName.toByteArray())
        }

        val inlineSBatchJob = "job"
        val workingDirectory = "/scratch/sduescience/p/files"
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(application, dummyTool),
            "/scratch/sduescience/p",
            workingDirectory,
            listOf(
                ValidatedFileForUpload(
                    irodsStat(fileName),
                    fileName,
                    "$workingDirectory/$fileName",
                    StoragePath(fileName)
                )
            ),
            inlineSBatchJob
        )

        withMockedAuthentication {
            withMockedSCPUpload(commandFailure = true) { service.handleAppEvent(event) }
        }

        assertTrue(emitSlot.isCaptured)
        val outputEvent = emitSlot.captured as AppEvent.Completed
        assertFalse(outputEvent.successful)
    }

    @Test
    fun testJobPreparationWithSSHFailure() {
        val application = app(
            "singlefile",
            listOf(VariableInvocationParameter(listOf("myFile"))),
            listOf(ApplicationParameter.InputFile("myFile", false))
        )
        createTemporaryApplication(application)

        val writerSlot = slot<OutputStream>()
        val fileName = "file.txt"
        every { files.get(match { it.name == fileName }, capture(writerSlot)) } answers {
            writerSlot.captured.write(fileName.toByteArray())
        }

        val inlineSBatchJob = "job"
        val workingDirectory = "/scratch/sduescience/p/files"
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(application, dummyTool),
            "/scratch/sduescience/p",
            workingDirectory,
            listOf(
                ValidatedFileForUpload(
                    irodsStat(fileName),
                    fileName,
                    "$workingDirectory/$fileName",
                    StoragePath(fileName)
                )
            ),
            inlineSBatchJob
        )

        withMockedAuthentication {
            withMockedSCPUpload(sshFailure = true) { service.handleAppEvent(event) }
        }

        assertTrue(emitSlot.isCaptured)
        val outputEvent = emitSlot.captured as AppEvent.Completed
        assertFalse(outputEvent.successful)
    }

    private inline fun <T> withMockedAuthentication(body: () -> T): T {
        objectMockk(TokenValidation).use {
            every { TokenValidation.validate(dummyToken.token) } returns dummyToken
            every { TokenValidation.validateOrNull(dummyToken.token) } returns dummyToken
            return body()
        }
    }

    private inline fun withMockedSCPUpload(
        commandFailure: Boolean = false,
        sshFailure: Boolean = false,
        body: () -> Unit
    ):
            Pair<List<String>, List<ByteArray>> {
        val names = ArrayList<String>()
        val writers = ArrayList<ByteArray>()

        staticMockk("dk.sdu.cloud.app.services.ssh.SCPKt").use {
            if (!sshFailure) {
                every {
                    sshConnection.scpUpload(
                        any(),
                        capture(names),
                        any(),
                        any(),
                        any()
                    )
                } answers {
                    if (!commandFailure) {
                        val os = ByteArrayOutputStream()

                        @Suppress("UNCHECKED_CAST")
                        val writer = call.invocation.args.last() as (OutputStream) -> Unit
                        writer(os)

                        writers.add(os.toByteArray())

                        0
                    } else {
                        writers.add(ByteArray(0))

                        1
                    }
                }
            } else {
                every { sshConnection.scpUpload(any(), any(), any(), any(), any()) } throws JSchException("Bad!")
            }
            body()
        }
        return Pair(names, writers)
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

    private val noParamsApplication = app(
        "noparams",
        invocation = listOf(WordInvocationParameter("noparms")),
        parameters = emptyList()
    )

    private fun app(
        name: String,
        invocation: List<InvocationParameter>,
        parameters: List<ApplicationParameter<*>>,
        fileGlobs: List<String> = emptyList()
    ): ApplicationDescription {
        return ApplicationDescription(
            tool = dummyTool.info,
            info = NameAndVersion(name, "1.0.0"),
            authors = listOf("Author"),
            prettyName = name,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            description = name,
            invocation = invocation,
            parameters = parameters,
            outputFileGlobs = fileGlobs
        )
    }

    private fun irodsStat(name: String): FileStat {
        return FileStat(
            StoragePath(name),
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            dummyTokenSubject,
            10L,
            "foo"
        )
    }

    private fun createTemporaryApplication(application: ApplicationDescription) {
        ApplicationDAO.inMemoryDB[application.info.name] = listOf(application)
    }

    private fun createTemporaryTool(tool: ToolDescription) {
        ToolDAO.inMemoryDB[tool.info.name] = listOf(tool)
    }
}
