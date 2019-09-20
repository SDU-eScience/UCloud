package dk.sdu.cloud.app.kubernetes.rpc

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.kubernetes.internalFollowStdStreamsRequest
import dk.sdu.cloud.app.kubernetes.jobVerifiedRequest
import dk.sdu.cloud.app.kubernetes.services.K8JobCreationService
import dk.sdu.cloud.app.kubernetes.services.K8JobMonitoringService
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.VncService
import dk.sdu.cloud.app.kubernetes.services.WebService
import dk.sdu.cloud.app.kubernetes.wrongSharedFileSystem
import dk.sdu.cloud.app.orchestrator.api.InternalStdStreamsResponse
import dk.sdu.cloud.app.orchestrator.api.SharedFileSystemMount
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.setJobSubmitParam
import dk.sdu.cloud.service.test.setJobSubmitid
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Test
import kotlin.test.assertEquals

class AppKubernetesControllerTest {

    private val k8JobMonitoringService = mockk<K8JobMonitoringService>()
    private val jobCreation = mockk<K8JobCreationService>()
    private val logService = mockk<K8LogService>()
    private val vncService = mockk<VncService>()
    private val webService = mockk<WebService>()

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        listOf(
            AppKubernetesController(
                k8JobMonitoringService,
                jobCreation,
                logService,
                vncService,
                webService,
                mockk(relaxed = true)
            )
        )
    }

    @Test
    fun `Test submit file`() {
        withKtorTest(
            setup,
            test = {
                val request = sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/kubernetes/submit",
                    user = TestUsers.admin,
                    configure = {
                        setJobSubmitid()
                        setJobSubmitParam()
                        setBody(byteArrayOf(1, 2, 3, 4))
                    }
                )

                //is not supported
                request.assertStatus(HttpStatusCode.BadRequest)
            }
        )
    }

    @Test
    fun `Test jobVerified`() {
        withKtorTest(
            setup,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/kubernetes/job-verified",
                    user = TestUsers.admin,
                    request = jobVerifiedRequest
                )

                request.assertSuccess()
            }
        )
    }

    @Test
    fun `Test jobVerified wrong backend mount`() {
        withKtorTest(
            setup,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/kubernetes/job-verified",
                    user = TestUsers.admin,
                    request = jobVerifiedRequest.copy(
                        _sharedFileSystemMounts = listOf(
                            SharedFileSystemMount(
                                wrongSharedFileSystem,
                                "mountedAt"
                            )
                        )
                    )
                )

                request.assertStatus(HttpStatusCode.BadRequest)
            }
        )
    }

    @Test
    fun `Test cleanup`() {
        coEvery { jobCreation.cleanup(any()) } just runs

        withKtorTest(
            setup,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/kubernetes/cleanup",
                    user = TestUsers.admin,
                    request = jobVerifiedRequest
                )

                request.assertSuccess()
            }
        )
    }

    @Test
    fun `Test follow`() {
        coEvery { logService.retrieveLogs(any(), any(), any()) } answers {
            Pair("logging", 1)
        }

        withKtorTest(
            setup,
            test = {
                val request = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/kubernetes/follow",
                    user = TestUsers.admin,
                    request = internalFollowStdStreamsRequest
                )

                request.assertSuccess()

                val response = defaultMapper.readValue<InternalStdStreamsResponse>(request.response.content!!)
                assertEquals("logging", response.stdout)
                assertEquals("", response.stderr)
                assertEquals(1, response.stdoutNextLine)
                assertEquals(0, response.stderrNextLine)
            }
        )
    }

}
