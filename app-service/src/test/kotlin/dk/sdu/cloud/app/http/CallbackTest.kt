package dk.sdu.cloud.app.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.AddStatusJob
import dk.sdu.cloud.app.api.JobCompletedRequest
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.StateChangeRequest
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.services.JobOrchestrator
import dk.sdu.cloud.app.services.verifiedJob
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.hibernate.Session
import org.junit.Test
import kotlin.test.assertEquals

private fun KtorApplicationTestSetupContext.configureCallbackServer(
    orchestrator: JobOrchestrator<Session>
): List<Controller> {
    return listOf(CallbackController(orchestrator))
}


class CallbackTest{

    @Test
    fun `addStatus test`() {
        withKtorTest(
            setup = {
                val orchestrator = mockk<JobOrchestrator<Session>>()

                every { orchestrator.handleAddStatus(any(), any(), any()) } just runs

                configureCallbackServer(orchestrator)

            },
            test = {
                val response = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/status",
                    user = TestUsers.service,
                    request = AddStatusJob("jobID", "new status")
                )
                response.assertSuccess()
            }
        )
    }

    @Test
    fun `change state test`() {
        withKtorTest(
            setup = {
                val orchestrator = mockk<JobOrchestrator<Session>>()

                coEvery { orchestrator.handleProposedStateChange(any(), any(), any()) } just runs

                configureCallbackServer(orchestrator)

            },
            test = {
                val response = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/state-change",
                    user = TestUsers.service,
                    request = StateChangeRequest("JobID", JobState.PREPARED, "new status")
                )
                response.assertSuccess()
            }
        )
    }

    @Test
    fun `submit file test`() {
        withKtorTest(
            setup = {
                val orchestrator = mockk<JobOrchestrator<Session>>()

                coEvery { orchestrator.handleIncomingFile(any(), any(), any(), any(), any(), any()) } just runs

                configureCallbackServer(orchestrator)

            },
            test = {
                val response = sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/submit",
                    user = TestUsers.service,
                    configure = {
                        addHeader("JobSubmit-Id", "jobId")
                        addHeader("JobSubmit-Path", "path/to/file")
                        addHeader("JobSubmit-Extraction", "true")
                        addHeader("Content-Length", "4")

                        setBody(byteArrayOf(1, 2, 3, 4))
                    }
                )
                response.assertSuccess()
            }
        )
    }

    @Test
    fun `submit file test - no length`() {
        withKtorTest(
            setup = {
                val orchestrator = mockk<JobOrchestrator<Session>>()
                configureCallbackServer(orchestrator)
            },
            test = {
                val response = sendRequest(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/submit",
                    user = TestUsers.service,
                    configure = {
                        addHeader("JobSubmit-Id", "jobId")
                        addHeader("JobSubmit-Path", "path/to/file")
                        addHeader("JobSubmit-Extraction", "true")
                        setBody(byteArrayOf(1, 2, 3, 4))
                    }
                )
                response.assertStatus(HttpStatusCode.LengthRequired)
            }
        )
    }

    @Test
    fun `lookup test`() {
        withKtorTest(
            setup = {
                val orchestrator = mockk<JobOrchestrator<Session>>()

                every { orchestrator.lookupOwnJob(any(), any()) } answers {
                    verifiedJob
                }

                configureCallbackServer(orchestrator)

            },
            test = {
                val response = sendRequest(
                    method = HttpMethod.Get,
                    path = "/api/app/compute/lookup/jobId",
                    user = TestUsers.service)

                response.assertSuccess()

                val results = defaultMapper.readValue<VerifiedJob>(response.response.content!!)
                assertEquals(verifiedJob.currentState, results.currentState)
                assertEquals(verifiedJob.id, results.id)

            }
        )
    }

    @Test
    fun `completed test`() {
        withKtorTest(
            setup = {
                val orchestrator = mockk<JobOrchestrator<Session>>()

                coEvery { orchestrator.handleJobComplete(any(), any(), any(), any()) } just runs

                configureCallbackServer(orchestrator)

            },
            test = {
                val response = sendJson(
                    method = HttpMethod.Post,
                    path = "/api/app/compute/completed",
                    user = TestUsers.service,
                    request = JobCompletedRequest(
                        "jobId",
                        SimpleDuration(1,0,0),
                        true
                    )
                )
                response.assertSuccess()
            }
        )
    }
}
