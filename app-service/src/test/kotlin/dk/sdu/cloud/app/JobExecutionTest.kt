package dk.sdu.cloud.app

import com.auth0.jwt.JWT
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpATTRS
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.app.services.ssh.*
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.client.RESTCallDescription
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.service.MappedEventProducer
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Ok
import dk.sdu.cloud.storage.api.*
import dk.sdu.cloud.storage.ext.*
import dk.sdu.cloud.storage.model.FileStat
import dk.sdu.cloud.storage.model.FileType
import dk.sdu.cloud.storage.model.StoragePath
import io.mockk.*
import io.mockk.impl.annotations.RelaxedMockK
import io.tus.java.client.TusUploader
import org.asynchttpclient.Response
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.*

class JobExecutionTest {
    val irodsConfiguration: StorageConfiguration = StorageConfiguration("irods", 1247, "tempZone")
    val sshUser = "nobody"
    val sBatchGenerator = SBatchGenerator()

    @RelaxedMockK
    lateinit var cloud: RefreshingJWTAuthenticatedCloud

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

    val emitSlot = ArrayList<AppEvent>()


    // ============================================================
    // ====================== Test resources ======================
    // ============================================================

    private val dummyTokenSubject = "test"
    private val dummyToken = JWT.decode(
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJ0ZXN0IiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
                "GxfHPZdY5aBZRt2g-ogPn6LfaG7MnAag-psqzquZKw8"
    )

    private val jobDirectiory = "/scratch/sduescience/p/"
    private val workingDirectory = "/scratch/sduescience/p/files/"

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

    private val txtFilesGlob = "*.txt"
    private val singleFileGlob = "b.txt"
    private val directoryGlob = "c/"
    private val filesUnderDirectoryGlob = "d/*"
    private val applicationWithOutputs = app(
        "appwithoutput",
        invocation = emptyList(),
        parameters = emptyList(),
        fileGlobs = listOf(txtFilesGlob, singleFileGlob, directoryGlob, filesUnderDirectoryGlob)
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
            "foo",
            FileType.FILE
        )
    }

    private fun createTemporaryApplication(application: ApplicationDescription) {
        ApplicationDAO.inMemoryDB[application.info.name] = listOf(application)
    }

    private fun createTemporaryTool(tool: ToolDescription) {
        ToolDAO.inMemoryDB[tool.info.name] = listOf(tool)
    }

    private fun <T> withMockScopes(vararg scopes: MockKUnmockKScope, body: () -> T): T {
        scopes.forEach { it.mock() }
        try {
            return body()
        } finally {
            scopes.reversed().forEach { it.unmock() }
        }
    }

    private fun tusHelperScope() = staticMockk("dk.sdu.cloud.storage.api.TusServiceHelpersKt")
    private fun scpScope() = staticMockk("dk.sdu.cloud.app.services.ssh.SCPKt")
    private fun sftpScope() = staticMockk("dk.sdu.cloud.app.services.ssh.SFTPKt")
    private fun zipScope() = staticMockk("dk.sdu.cloud.app.services.ssh.ZIPKt")
    private fun tusScope() = objectMockk(TusDescriptions)

    // =========================================
    // TESTS
    // =========================================

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

        val applications = listOf(noParamsApplication, applicationWithOutputs)
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
                    "",
                    FileType.FILE
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
        val captured = emitSlot.first() as AppEvent.Validated
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
                        "",
                        FileType.FILE
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
        val captured = emitSlot.first() as AppEvent.Validated
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
                        "",
                        FileType.FILE
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
        val captured = emitSlot.first() as AppEvent.Validated
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

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.Completed
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
                withMockScopes(sftpScope()) {
                    every { sshConnection.mkdir(any(), any()) } returns 0
                    service.handleAppEvent(event)
                }
            }
        }

        assertTrue(emitSlot.isNotEmpty())
        emitSlot.first() as AppEvent.Prepared

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
            withMockScopes(sftpScope()) {
                every { sshConnection.mkdir(any(), any()) } returns 0
                withMockedSCPUpload { service.handleAppEvent(event) }
            }
        }

        assertTrue(emitSlot.isNotEmpty())
        emitSlot.first() as AppEvent.Prepared

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
            withMockScopes(sftpScope()) {
                every { sshConnection.mkdir(any(), any()) } returns 0
                withMockedSCPUpload { service.handleAppEvent(event) }
            }
        }

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.Completed
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
            withMockScopes(sftpScope()) {
                every { sshConnection.mkdir(any(), any()) } returns 0
                withMockedSCPUpload(commandFailure = true) { service.handleAppEvent(event) }
            }
        }

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.Completed
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
        val event = AppEvent.Validated(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyToken.token,
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(application, dummyTool),
            jobDirectiory,
            workingDirectory,
            listOf(
                ValidatedFileForUpload(
                    irodsStat(fileName),
                    fileName,
                    "$workingDirectory$fileName",
                    StoragePath(fileName)
                )
            ),
            inlineSBatchJob
        )

        withMockedAuthentication {
            withMockScopes(sftpScope()) {
                every { sshConnection.mkdir(any(), any()) } returns 0
                withMockedSCPUpload(sshFailure = true) { service.handleAppEvent(event) }
            }
        }

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.Completed
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

    @Test
    fun testValidJobScheduling() {
        val event = AppEvent.Prepared(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
            "nobody",
            jobDirectiory,
            workingDirectory,
            jobDirectiory + "job.sh"
        )

        val batchJobs = withMockedSBatch(jobId = 123L) {
            service.handleAppEvent(event)
        }

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.ScheduledAtSlurm
        assertEquals(123L, outputEvent.slurmId)

        assertEquals(1, batchJobs.size)
        assertEquals(event.jobScriptLocation, batchJobs.first())
    }

    @Test
    fun testJobSchedulingWithSlurmFailure() {
        val event = AppEvent.Prepared(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
            "nobody",
            jobDirectiory,
            workingDirectory,
            jobDirectiory + "job.sh"
        )

        withMockedSBatch(commandFailure = true, jobId = 123L) {
            service.handleAppEvent(event)
        }

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
        assertFalse(outputEvent.successful)
    }

    @Test
    fun testJobSchedulingWithSSHFailure() {
        val event = AppEvent.Prepared(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            dummyTokenSubject,
            ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
            "nobody",
            jobDirectiory,
            workingDirectory,
            jobDirectiory + "job.sh"
        )

        withMockedSBatch(sshFailure = true, jobId = 123L) {
            service.handleAppEvent(event)
        }

        assertTrue(emitSlot.isNotEmpty())
        val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
        assertFalse(outputEvent.successful)
    }

    private inline fun withMockedSBatch(
        commandFailure: Boolean = false,
        sshFailure: Boolean = false,
        jobId: Long,
        body: () -> Unit
    ): List<String> {
        val names = ArrayList<String>()
        staticMockk("dk.sdu.cloud.app.services.ssh.SBatchKt").use {
            if (!sshFailure) {
                every { sshConnection.sbatch(capture(names)) } answers {
                    if (commandFailure) SBatchSubmissionResult(1, "Bad", null)
                    else SBatchSubmissionResult(0, "OK", jobId)
                }
            } else {
                every { sshConnection.sbatch(any()) } throws JSchException("Bad!")
            }
            body()
        }

        return names
    }

    @Test
    fun testScheduledAtSlurm() {
        val slurmId = 123L
        val systemId = UUID.randomUUID().toString()
        val owner = dummyTokenSubject
        val sshUser = dummyTokenSubject

        val event = AppEvent.ScheduledAtSlurm(
            systemId,
            System.currentTimeMillis(),
            owner,
            ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
            sshUser,
            jobDirectiory,
            workingDirectory,
            slurmId
        )

        service.handleAppEvent(event)

        verify {
            slurmPollAgent.startTracking(slurmId)
            jobsDao.updateJobWithSlurmInformation(systemId, sshUser, jobDirectiory, workingDirectory, slurmId)
        }

        assertEquals(0, emitSlot.size)
    }

    private val completedInSlurmEvent = AppEvent.CompletedInSlurm(
        UUID.randomUUID().toString(),
        System.currentTimeMillis(),
        dummyTokenSubject,
        ApplicationWithOptionalDependencies(applicationWithOutputs, dummyTool),
        dummyTokenSubject,
        jobDirectiory,
        workingDirectory,
        true,
        123L
    )

    @Test
    fun testShippingResultsWithDirectoryFailure() {
        objectMockk(FileDescriptions).use {
            val directoryCall = mockk<RESTCallDescription<CreateDirectoryRequest, Unit, CommonErrorMessage>>()
            every { FileDescriptions.createDirectory } returns directoryCall

            coEvery { directoryCall.call(any(), any()) } returns RESTResponse.Err(mockk(relaxed = true))

            service.handleAppEvent(completedInSlurmEvent)
            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
            assertFalse(outputEvent.successful)
        }
    }

    @Test
    fun testShippingResultsWithNoOutputFiles() {
        withMockScopes(objectMockk(FileDescriptions), sftpScope()) {
            val directoryCall = mockk<RESTCallDescription<CreateDirectoryRequest, Unit, CommonErrorMessage>>()
            every { FileDescriptions.createDirectory } returns directoryCall
            coEvery { directoryCall.call(any(), any()) } returns RESTResponse.Ok(mockk(relaxed = true), Unit)

            every { sshConnection.lsWithGlob(any(), any()) } returns emptyList()

            service.handleAppEvent(completedInSlurmEvent)
            assertEquals(1, emitSlot.size)

            val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
            assertTrue(outputEvent.successful)

            completedInSlurmEvent.appWithDependencies.application.outputFileGlobs.forEach {
                verify { sshConnection.lsWithGlob(workingDirectory, it) }
            }
        }
    }

    @Test
    fun testShippingResultWithSingleFile() {
        withAllShippingScopes {
            mockCreateDirectoryCall(success = true)
            every { sshConnection.lsWithGlob(any(), any()) } returns emptyList()

            run {
                // Single file present
                every { sshConnection.lsWithGlob(workingDirectory, singleFileGlob) } returns listOf(
                    LSWithGlobResult(workingDirectory + singleFileGlob, 10L)
                )

                mockStatForRemoteFile(workingDirectory + singleFileGlob, 10L, false)
            }

            val creationCommands = mockTusUploadCreationAndGetCommands()
            val remoteFiles = mockScpDownloadAndGetFileList()
            val (_, _, tusUploader) = mockTusUploaderAndGetLocationsAndSizes()

            // Run tests
            service.handleAppEvent(completedInSlurmEvent)

            // Check results
            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
            assertTrue(outputEvent.successful)


            completedInSlurmEvent.appWithDependencies.application.outputFileGlobs.forEach {
                verify { sshConnection.lsWithGlob(workingDirectory, it) }
            }

            verify(exactly = 1) { tusUploader.start(any()) }

            assertEquals(1, creationCommands.size)
            assertEquals(singleFileGlob, creationCommands.first().fileName)

            assertEquals(1, remoteFiles.size)
            assertEquals(workingDirectory + singleFileGlob, remoteFiles.first())
        }
    }

    @Test
    fun testShippingResultWithMultipleFiles() {
        withAllShippingScopes {
            mockCreateDirectoryCall(success = true)
            every { sshConnection.lsWithGlob(any(), any()) } returns emptyList()
            val fileNames = listOf("1.txt", "2.txt", "3.txt")

            run {
                every { sshConnection.lsWithGlob(workingDirectory, txtFilesGlob) } returns fileNames.map {
                    LSWithGlobResult(workingDirectory + it, 10L)
                }

                fileNames.forEach {
                    mockStatForRemoteFile(workingDirectory + it, 10L, false)
                }
            }

            val creationCommands = mockTusUploadCreationAndGetCommands()
            val remoteFiles = mockScpDownloadAndGetFileList()
            val (_, _, tusUploader) = mockTusUploaderAndGetLocationsAndSizes()

            // Run tests
            service.handleAppEvent(completedInSlurmEvent)

            // Check results
            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
            assertTrue(outputEvent.successful)

            completedInSlurmEvent.appWithDependencies.application.outputFileGlobs.forEach {
                verify { sshConnection.lsWithGlob(workingDirectory, it) }
            }

            verify(exactly = fileNames.size) { tusUploader.start(any()) }

            assertEquals(fileNames.size, creationCommands.size)
            val createdFiles = creationCommands.map { it.fileName!! }.sorted()
            assertEquals(fileNames.sorted(), createdFiles)

            assertEquals(fileNames.size, remoteFiles.size)
            val expectedRemotes = fileNames.map { workingDirectory + it }.sorted()
            val actualRemote = remoteFiles.sorted()
            assertEquals(expectedRemotes, actualRemote)
        }
    }

    @Test
    fun testShippingResultsWithDirectory() {
        withAllShippingScopes {
            mockCreateDirectoryCall(success = true)
            every { sshConnection.lsWithGlob(any(), any()) } returns emptyList()
            val fileNames = "c/"

            run {
                every { sshConnection.lsWithGlob(workingDirectory, directoryGlob) } returns listOf(fileNames).map {
                    LSWithGlobResult(workingDirectory + it, 10L)
                }

                fileNames.forEach {
                    mockStatForRemoteFile(workingDirectory + it, 10L, true)
                }
            }

            val creationCommands = mockTusUploadCreationAndGetCommands()
            val remoteFiles = mockScpDownloadAndGetFileList()
            val (_, _, tusUploader) = mockTusUploaderAndGetLocationsAndSizes()
            val (zipOutputs, zipInputs) = mockZipCall(commandFailure = false)
            val expectedZipOutput = workingDirectory + "c.zip"
            mockStatForRemoteFile(expectedZipOutput, 10L, false)

            // Run tests
            service.handleAppEvent(completedInSlurmEvent)

            // Check results
            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
            assertTrue(outputEvent.successful)


            completedInSlurmEvent.appWithDependencies.application.outputFileGlobs.forEach {
                verify { sshConnection.lsWithGlob(workingDirectory, it) }
            }

            verify(exactly = 1) { tusUploader.start(any()) }

            assertEquals(1, creationCommands.size)
            assertEquals(expectedZipOutput.substringAfterLast('/'), creationCommands.first().fileName)

            assertEquals(1, remoteFiles.size)
            assertEquals(expectedZipOutput, remoteFiles.first())

            verify(exactly = 1) { sshConnection.stat(expectedZipOutput) }
            assertEquals(1, zipOutputs.size)
            assertEquals(expectedZipOutput, zipOutputs.first())
            assertEquals(1, zipInputs.size)
            assertEquals(workingDirectory + fileNames.removeSuffix("/"), zipInputs.first())
        }
    }

    // TODO Is this a good idea?
    @Test(expected = IllegalStateException::class)
    fun testShippingResultWithSingleFileUploadCreationFailure() {
        withAllShippingScopes {
            mockCreateDirectoryCall(success = true)
            every { sshConnection.lsWithGlob(any(), any()) } returns emptyList()

            run {
                // Single file present
                every { sshConnection.lsWithGlob(workingDirectory, singleFileGlob) } returns listOf(
                    LSWithGlobResult(workingDirectory + singleFileGlob, 10L)
                )

                mockStatForRemoteFile(workingDirectory + singleFileGlob, 10L, false)
            }

            mockTusUploadCreationAndGetCommands(commandFailure = true)
            mockScpDownloadAndGetFileList()
            mockTusUploaderAndGetLocationsAndSizes()

            // Run tests
            service.handleAppEvent(completedInSlurmEvent)
        }
    }

    @Test
    fun testShippingResultWithSingleFileUploadTransferFailure() {
        withAllShippingScopes {
            mockCreateDirectoryCall(success = true)
            every { sshConnection.lsWithGlob(any(), any()) } returns emptyList()

            run {
                // Single file present
                every { sshConnection.lsWithGlob(workingDirectory, singleFileGlob) } returns listOf(
                    LSWithGlobResult(workingDirectory + singleFileGlob, 10L)
                )

                mockStatForRemoteFile(workingDirectory + singleFileGlob, 10L, false)
            }

            mockTusUploadCreationAndGetCommands()
            mockScpDownloadAndGetFileList(commandFailure = true)
            mockTusUploaderAndGetLocationsAndSizes()

            // Run tests
            service.handleAppEvent(completedInSlurmEvent)

            // Check results
            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.ExecutionCompleted
            assertFalse(outputEvent.successful)
        }
    }


    private fun withAllShippingScopes(body: () -> Unit) {
        withMockScopes(
            objectMockk(FileDescriptions),

            tusScope(),
            tusHelperScope(),

            sftpScope(),
            scpScope(),
            zipScope()
        ) {
            body()
        }
    }

    data class DirectoryZipMock(val outputs: List<String>, val inputs: List<String>)

    private fun mockZipCall(commandFailure: Boolean = false): DirectoryZipMock {
        val outputs = ArrayList<String>()
        val inputs = ArrayList<String>()
        if (!commandFailure) {
            every { sshConnection.createZipFileOfDirectory(capture(outputs), capture(inputs)) } returns 0
        } else {
            every { sshConnection.createZipFileOfDirectory(capture(outputs), capture(inputs)) } returns 1
        }
        return DirectoryZipMock(outputs, inputs)
    }

    private fun mockCreateDirectoryCall(
        success: Boolean
    ): RESTCallDescription<CreateDirectoryRequest, Unit, CommonErrorMessage> {
        val directoryCall = mockk<RESTCallDescription<CreateDirectoryRequest, Unit, CommonErrorMessage>>()
        every { FileDescriptions.createDirectory } returns directoryCall
        if (success) {
            coEvery { directoryCall.call(any(), any()) } returns RESTResponse.Ok(mockk(relaxed = true), Unit)
        } else {
            coEvery { directoryCall.call(any(), any()) } returns RESTResponse.Err(mockk(relaxed = true))
        }
        return directoryCall
    }

    private fun mockTusUploadCreationAndGetCommands(commandFailure: Boolean = false): List<UploadCreationCommand> {
        // Upload creation
        val commands = ArrayList<UploadCreationCommand>()
        val createMock = mockk<RESTCallDescription<UploadCreationCommand, Unit, Unit>>()
        every { TusDescriptions.create } returns createMock

        if (!commandFailure) {
            val response = mockk<Response>(relaxed = true)
            every { response.headers["Location"] } returns "https://cloud.sdu.dk/api/tus/upload-id"
            coEvery { createMock.call(capture(commands), any()) } returns RESTResponse.Ok(
                response,
                Unit
            )
        } else {
            coEvery { createMock.call(capture(commands), any()) } returns RESTResponse.Err(
                mockk(relaxed = true),
                Unit
            )
        }

        return commands
    }

    data class TusUploaderMock(val locations: List<String>, val sizes: List<Int>, val uploader: TusUploader)

    private fun mockTusUploaderAndGetLocationsAndSizes(): TusUploaderMock {
        val uploadLocations = ArrayList<String>()
        val uploadSizes = ArrayList<Int>()
        val tusUploader = mockk<TusUploader>(relaxed = true)
        every {
            TusDescriptions.uploader(
                any(),
                capture(uploadLocations),
                capture(uploadSizes),
                any(),
                any()
            )
        } returns tusUploader

        every { tusUploader.start(any()) } just Runs
        return TusUploaderMock(uploadLocations, uploadSizes, tusUploader)
    }

    private fun mockScpDownloadAndGetFileList(commandFailure: Boolean = false): List<String> {
        val remoteFiles = ArrayList<String>()
        if (!commandFailure) {
            every {
                sshConnection.scpDownload(
                    capture(remoteFiles),
                    any()
                )
            } answers {
                @Suppress("UNCHECKED_CAST")
                val reader = call.invocation.args.last() as (InputStream) -> Unit
                reader(ByteArrayInputStream(ByteArray(0)))
                0
            }
        } else {
            every {
                sshConnection.scpDownload(
                    capture(remoteFiles),
                    any()
                )
            } returns 1
        }

        return remoteFiles
    }

    private fun mockStatForRemoteFile(absolutePath: String, size: Long, isDir: Boolean) {
        val returnedFile: SftpATTRS = mockk(relaxed = true)
        every { sshConnection.stat(absolutePath) } returns returnedFile
        every { returnedFile.size } returns size
        every { returnedFile.isDir } returns isDir
    }

    @Test
    fun testCleanup() {
        withMockScopes(sftpScope()) {
            val event = AppEvent.ExecutionCompleted(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                dummyTokenSubject,
                ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
                "nobody",
                jobDirectiory,
                workingDirectory,
                true,
                "Foo"
            )

            every { sshConnection.rm(any(), any(), any()) } returns 0

            service.handleAppEvent(event)

            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.Completed
            assertEquals(event.successful, outputEvent.successful)

            verify { sshConnection.rm(jobDirectiory, true, true) }
        }
    }

    @Test
    fun testCleanupWithDeletionFailure() {
        withMockScopes(sftpScope()) {
            val event = AppEvent.ExecutionCompleted(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                dummyTokenSubject,
                ApplicationWithOptionalDependencies(noParamsApplication, dummyTool),
                "nobody",
                jobDirectiory,
                workingDirectory,
                true,
                "Foo"
            )

            every { sshConnection.rm(any(), any(), any()) } returns 1

            service.handleAppEvent(event)

            assertEquals(1, emitSlot.size)
            val outputEvent = emitSlot.first() as AppEvent.Completed
            assertEquals(event.successful, outputEvent.successful)

            verify { sshConnection.rm(jobDirectiory, true, true) }
        }
    }
}
