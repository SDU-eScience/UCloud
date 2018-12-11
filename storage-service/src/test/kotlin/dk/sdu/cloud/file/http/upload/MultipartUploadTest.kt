package dk.sdu.cloud.file.http.upload

import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.files.TestContext
import dk.sdu.cloud.file.http.files.setUser
import dk.sdu.cloud.file.services.BulkUploadService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.storage.util.createDummyFSInRoot
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.Application
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.content.PartData
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.io.streams.asInput
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertEquals

class MultipartUploadTest {
    fun Application.createService(builder: File.() -> Unit = File::createDummyFSInRoot) {
        return createService(createFS(builder))
    }

    fun Application.createService(root: String) {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
        return createService(runner, fs)
    }

    fun Application.createService(
        runner: FSCommandRunnerFactory<UnixFSCommandRunner>,
        fs: LowLevelFileSystemInterface<UnixFSCommandRunner>
    ) {
        val micro = initializeMicro()
        micro.install(HibernateFeature)
        TestContext.micro = micro
        val storageEventProducer = micro.kafka.producer.forStream(StorageEvents.events)
        val coreFs = CoreFileSystemService(fs, storageEventProducer)

        val bulkUpload = BulkUploadService(coreFs)
        val sensitivityService = FileSensitivityService(fs, storageEventProducer)
        val controller = MultiPartUploadController(runner, coreFs, bulkUpload, sensitivityService)

        installDefaultFeatures(micro)

        routing {
            configureControllers(controller)
        }
    }

    @Test
    fun `test missing permissions`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val runner = mockk<FSCommandRunnerFactory<UnixFSCommandRunner>>(relaxed = true)
                    val userContext = mockk<UnixFSCommandRunner>(relaxed = true)
                    every { runner.invoke(any()) } returns userContext

                    val fs = mockk<UnixFileSystem>()
                    coEvery { fs.openForWriting(any(), any(), any()) } throws FSException.PermissionException()
                    coEvery { fs.write(any(), any()) } throws FSException.PermissionException()

                    createService(runner, fs)
                },

                test = {
                    val response = handleRequest(HttpMethod.Post, "/api/files/upload/") {
                        setUser("user")
                        val boundary = UUID.randomUUID().toString()

                        addHeader(
                            HttpHeaders.ContentType,
                            ContentType.MultiPart.FormData.withParameter("boundary", boundary).toString()
                        )

                        fun partName(name: String, type: ContentDisposition = ContentDisposition.Inline): Headers {
                            return Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    type.withParameter(ContentDisposition.Parameters.Name, name)
                                )
                            }
                        }

                        setBody(
                            boundary, listOf(
                                PartData.FormItem(
                                    "/home/somewhere/foo",
                                    {},
                                    partName("location")
                                ),

                                PartData.FormItem(
                                    SensitivityLevel.CONFIDENTIAL.name,
                                    {},
                                    partName("sensitivity")
                                ),

                                PartData.FileItem(
                                    { ByteReadChannel("hello, world!").toInputStream().asInput() },
                                    {},
                                    partName(
                                        "upload",
                                        ContentDisposition.File.withParameter(
                                            ContentDisposition.Parameters.FileName,
                                            "fileName"
                                        )
                                    )
                                )
                            )
                        )
                    }.response

                    assertEquals(HttpStatusCode.Forbidden, response.status())
                }
            )
        }
    }
}
