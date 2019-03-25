package dk.sdu.cloud.indexing.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(
        io.ktor.http.HttpHeaders.Authorization,
        "Bearer ${TokenValidationMock.createTokenForUser(username, role)}"
    )
}


private fun configureLookupServer(reverseLookupService: ElasticQueryService): List<Controller> {
    return listOf(LookupController(reverseLookupService))
}

class LookupTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `Lookup Test`() {
        withKtorTest(
            setup = {
                val reverseLookupService = mockk<ElasticQueryService>()
                every { reverseLookupService.reverseLookupBatch(any()) } returns listOf("This is what I found")
                configureLookupServer(reverseLookupService)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/indexing/lookup/reverse?fileId=1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    val obj = mapper.readTree(response.content)
                    assertEquals("This is what I found", obj["canonicalPath"].first().textValue())
                }
            }

        )
    }

    @Test
    fun `Lookup test - to many files exception thrown`() {
        withKtorTest(
            setup = {
                val reverseLookupService = mockk<ElasticQueryService>()
                every { reverseLookupService.reverseLookupBatch(any()) } answers {
                    throw RPCException("Bad request. Too many file IDs", HttpStatusCode.BadRequest)
                }
                configureLookupServer(reverseLookupService)
            },

            test = {
                with(engine) {
                    val response =
                        handleRequest(HttpMethod.Get, "/api/indexing/lookup/reverse?fileId=1") {
                            addHeader("Job-Id", UUID.randomUUID().toString())
                            setUser(role = Role.ADMIN)
                        }.response

                    assertEquals(HttpStatusCode.BadRequest, response.status())
                }
            }
        )
    }
}
