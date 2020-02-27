package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.orchestrator.api.CancelRequest
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.JobWithStatus
import dk.sdu.cloud.app.orchestrator.api.QueryWebParametersRequest
import dk.sdu.cloud.app.orchestrator.api.StartJobRequest
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.client.OutgoingHostResolver
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Loggable
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConcurrentAppTest(
    private val userA: UserAndClient,
    private val concurrency: Int,
    private val hostResolver: OutgoingHostResolver
) {
    private object Calls : CallDescriptionContainer("app.compute") {
        val dummy = call<Unit, Unit, CommonErrorMessage>("dummy") {
            auth {
                access = AccessRight.READ
            }

            http {
                method = HttpMethod.Get
                path {
                    using("/api/app/compute")
                }
            }
        }
    }

    suspend fun runTest() {
        withContext(Dispatchers.IO) {
            with(userA) {
                (0 until concurrency).map { tId ->
                    val httpClientNoFollow = HttpClient(OkHttp) {
                        followRedirects = false
                    }

                    val httpClient = HttpClient(OkHttp)

                    launch {
                        val jobId = JobDescriptions.start.call(
                            StartJobRequest(
                                NameAndVersion("httpbin", "0.1.0"),
                                parameters = emptyMap(),
                                maxTime = SimpleDuration(0, 10, 0),
                                acceptSameDataRetry = true
                            ),
                            client
                        ).orThrow().jobId

                        log.info("Application was started: $jobId")
                        lateinit var status: JobWithStatus
                        retrySection(attempts = 3000, delay = 1_000) {
                            log.info("Checking job status")
                            status = JobDescriptions.findById.call(FindByStringId(jobId), userA.client).orThrow()
                            require(status.state == JobState.RUNNING) { "Current job state is: ${status.state}" }
                            require(status.outputFolder != null)
                        }

                        val webParams = JobDescriptions.queryWebParameters.call(
                            QueryWebParametersRequest(jobId),
                            client
                        ).orThrow()

                        val endpoint = hostResolver.resolveEndpoint(Calls.dummy)
                        val resp = httpClientNoFollow
                            .get<HttpResponse>(
                                endpoint.scheme ?: "http",
                                endpoint.host,
                                when (endpoint.scheme) {
                                    "http" -> 80
                                    "https" -> 443
                                    else -> 80
                                },
                                webParams.path,
                                block = {
                                    header("Cookie", "refreshToken=${userA.refreshToken}")
                                }
                            )

                        log.info("Got back path: ${webParams.path} and $resp")

                        val httpbinResp = httpClient.get<HttpResponse>(resp.headers["Location"]!!.removeSuffix("/") + "/get") {
                            header("Cookie", "appRefreshToken=${userA.refreshToken}")
                        }

                        JobDescriptions.cancel.call(
                            CancelRequest(jobId),
                            client
                        ).orThrow()

                        require(httpbinResp.status.isSuccess())
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
