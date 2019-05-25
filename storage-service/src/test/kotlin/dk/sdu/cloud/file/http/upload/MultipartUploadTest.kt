package dk.sdu.cloud.file.http.upload

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.files.TestContext
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.LowLevelFileSystemInterface
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import dk.sdu.cloud.storage.util.createDummyFSInRoot
import dk.sdu.cloud.storage.util.createFS
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.append
import io.ktor.http.content.PartData
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.io.streams.asInput
import org.junit.Test
import java.io.File
import java.util.*
import kotlin.test.assertEquals

class MultipartUploadTest {
    fun KtorApplicationTestSetupContext.createService(builder: File.() -> Unit = File::createDummyFSInRoot): List<Controller> {
        return createService(createFS(builder))
    }

    fun KtorApplicationTestSetupContext.createService(root: String): List<Controller> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        return createService(runner, fs)
    }

    fun KtorApplicationTestSetupContext.createService(
        runner: FSCommandRunnerFactory<LinuxFSRunner>,
        fs: LowLevelFileSystemInterface<LinuxFSRunner>
    ): List<Controller> {
        TestContext.micro = micro
        val storageEventProducer =
            StorageEventProducer(micro.eventStreamService.createProducer(StorageEvents.events), {})
        val coreFs = CoreFileSystemService(fs, storageEventProducer)

        val sensitivityService = FileSensitivityService(fs, storageEventProducer)
        val controller = MultiPartUploadController(
            AuthenticatedClient(micro.client, OutgoingHttpCall) {},
            runner,
            coreFs,
            sensitivityService
        )

        return listOf(controller)
    }

    @Test
    fun `test missing permissions`() {
        withKtorTest(
            setup = {
                val runner = mockk<FSCommandRunnerFactory<LinuxFSRunner>>(relaxed = true)
                val userContext = mockk<LinuxFSRunner>(relaxed = true)
                coEvery { runner.invoke(any()) } returns userContext

                val fs = mockk<LinuxFS>()
                coEvery { fs.openForWriting(any(), any(), any()) } throws FSException.PermissionException()
                coEvery { fs.write(any(), any()) } throws FSException.PermissionException()

                createService(runner, fs)
            },

            test = {
                val response = sendRequest(HttpMethod.Post, "/api/files/upload/", TestUsers.user) {
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
