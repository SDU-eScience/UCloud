package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.app.orchestrator.api.CancelRequest
import dk.sdu.cloud.app.orchestrator.api.FollowStdStreamsResponse
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.ListRecentRequest
import dk.sdu.cloud.app.orchestrator.api.MachineReservation
import dk.sdu.cloud.app.orchestrator.api.QueryInternalVncParametersResponse
import dk.sdu.cloud.app.orchestrator.api.QueryInternalWebParametersResponse
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.orchestrator.services.JobQueryService
import dk.sdu.cloud.app.orchestrator.services.StreamFollowService
import dk.sdu.cloud.app.orchestrator.services.VncService
import dk.sdu.cloud.app.orchestrator.services.WebService
import dk.sdu.cloud.app.orchestrator.utils.jobWithStatus
import dk.sdu.cloud.app.orchestrator.utils.normAppDesc
import dk.sdu.cloud.app.orchestrator.utils.verifiedJobWithAccessToken
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Job
import org.hibernate.Session
import org.junit.Test

private fun KtorApplicationTestSetupContext.configureCallbackServer(
    jobQueryService: JobQueryService<Session>,
    orchestrator: JobOrchestrator<Session>,
    streamFollowService: StreamFollowService,
    userClientFactory: (String?, String?) -> AuthenticatedClient,
    serviceClient: AuthenticatedClient,
    vncService: VncService<Session>,
    webService: WebService<Session>,
    machineTypes: List<MachineReservation>
): List<Controller> {
    return listOf(JobController(
        jobQueryService,
        orchestrator,
        streamFollowService,
        userClientFactory,
        serviceClient,
        vncService,
        webService,
        machineTypes
    ))
}

class JobTest{

    @Test
    fun`start, find, listRecent, follow cancel job test Controller CC`() {
        withKtorTest(
            setup = {
                val jobQueryService = mockk<JobQueryService<Session>>()
                val orchestrator = mockk<JobOrchestrator<Session>>()
                val streamFollowService = mockk<StreamFollowService>()
                val userClientFactory: (String?, String?) -> AuthenticatedClient =
                    { accessToken, refreshToken ->
                        ClientMock.authenticatedClient
                    }
                val serviceClient = ClientMock.authenticatedClient
                val vncService = mockk<VncService<Session>>()
                val webService = mockk<WebService<Session>>()
                val machineTypes = listOf( MachineReservation("ReservationName", 2, 2))

                ClientMock.mockCallSuccess(
                    AuthDescriptions.tokenExtension,
                    TokenExtensionResponse("accessToken", "csrfToken", "refreshToken")
                )

                coEvery { orchestrator.startJob(any(), any(), any(), any(), any()) } answers {
                    "IdOfJob"
                }

                coEvery { jobQueryService.findById(any(), any()) } returns verifiedJobWithAccessToken
                coEvery { jobQueryService.asJobWithStatus(any()) } returns jobWithStatus

                coEvery { jobQueryService.listRecent(
                    any(), any(), any(), any()
                ) } answers {
                    val resultPage = Page(
                        1,
                        10,
                        0,
                        listOf(jobWithStatus.copy(state = JobState.FAILURE))
                    )
                    resultPage
                }

                coEvery { streamFollowService.followStreams(any(), any())} answers {
                    FollowStdStreamsResponse(
                        "output",
                        10,
                        "error",
                        10 ,
                        NameAndVersion("name", "verison"),
                        JobState.RUNNING,
                        "status",
                        false,
                        null,
                        null,
                        "ID",
                        null,
                        metadata = normAppDesc.metadata
                    )
                }

                coEvery { streamFollowService.followWSStreams(any(), any(), any())} answers {
                    mockk<Job>()
                }

                coEvery{ webService.queryWebParameters(any(), any())} answers {
                    QueryInternalWebParametersResponse("type")
                }

                coEvery{ vncService.queryVncParameters(any(), any())} answers {
                    QueryInternalVncParametersResponse("type")
                }

                coEvery { orchestrator.handleProposedStateChange(any(), any(), any(), any()) } just Runs

                configureCallbackServer(
                    jobQueryService,
                    orchestrator,
                    streamFollowService,
                    userClientFactory,
                    serviceClient,
                    vncService,
                    webService,
                    machineTypes
                )
            },
            test = {
                val startRequest = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/hpc/jobs",
                    user = TestUsers.user,
                    request = StartJobRequest(
                        NameAndVersion(normAppDesc.metadata.name, normAppDesc.metadata.version),
                        "name",
                        mapOf("value" to 1),
                        1,
                        1,
                        SimpleDuration(1,0,0),
                        "backend",
                        null,
                        emptyList(),
                        emptyList(),
                        "ReservationName"
                    )
                )

                startRequest.assertSuccess()

                val findRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/hpc/jobs/idOfJob",
                    user = TestUsers.user
                )

                findRequest.assertSuccess()

                val listRequest = sendJson(
                    method = HttpMethod.Get,
                    path = "/api/hpc/jobs",
                    user = TestUsers.user,
                    request = ListRecentRequest(null, null)
                )
                listRequest.assertSuccess()

                val followService = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/hpc/jobs/follow/jobID",
                    user = TestUsers.user,
                    params = mapOf(
                        "stderrLineStart" to 0,
                        "stderrMaxLines" to 10,
                        "stdoutLineStart" to 0,
                        "stdoutMaxLines" to 10)
                )

                followService.assertSuccess()

                val queryWebRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/hpc/jobs/query-web/jobid",
                    user = TestUsers.user
                )
                queryWebRequest.assertSuccess()

                val queryVncRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/hpc/jobs/query-vnc/jobid",
                    user = TestUsers.user
                )
                queryVncRequest.assertSuccess()

                val machineTypeRequest = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/hpc/jobs/machine-types",
                    user = TestUsers.user
                )
                machineTypeRequest.assertSuccess()

                val cancelRequest = sendJson(
                    method = HttpMethod.Delete,
                    path = "api/hpc/jobs",
                    user = TestUsers.user,
                    request = CancelRequest("jobID")
                )
                cancelRequest.assertSuccess()
            }
        )
    }
}
