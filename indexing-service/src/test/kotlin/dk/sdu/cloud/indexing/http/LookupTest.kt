package dk.sdu.cloud.indexing.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.indexing.services.ReverseLookupService
import dk.sdu.cloud.indexing.utils.withAuthMock
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installDefaultFeatures
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

private fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(io.ktor.http.HttpHeaders.Authorization, "Bearer $username/$role")
}

private fun Application.configureBaseServer(vararg controllers: Controller) {
    installDefaultFeatures(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        requireJobId = true
    )

    routing {
        configureControllers(*controllers)
    }
}

private fun Application.configureLookupServer(reverseLookupService: ReverseLookupService) {
    configureBaseServer(LookupController(reverseLookupService))
}

class LookupTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `Lookup Test`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val reverseLookupService = mockk<ReverseLookupService>()
                    every { reverseLookupService.reverseLookupBatch(any()) } returns listOf("This is what I found")
                    configureLookupServer(reverseLookupService)
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/indexing/lookup/reverse?fileId=1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    val obj = mapper.readTree(response.content)
                    assertEquals("This is what I found", obj["canonicalPath"].first().textValue())
                }

            )
        }
    }

    @Test
    fun `Lookup test - to many files exception thrown`() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val reverseLookupService = mockk<ReverseLookupService>()
                    every { reverseLookupService.reverseLookupBatch(any()) } answers {
                        throw RPCException("Bad request. Too many file IDs", HttpStatusCode.BadRequest)
                    }
                    configureLookupServer(reverseLookupService)
                },

                test = {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/indexing/lookup/reverse?fileId=1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            )
        }
    }
}
