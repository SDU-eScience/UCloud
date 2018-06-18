package dk.sdu.cloud.storage.http.files

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.definition
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.api.StorageFile
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.services.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.services.createDummyFS
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.install
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
}

class ListAtPathTests {
    private val mapper = jacksonObjectMapper()

    @Test
    fun listAtPathTest() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/user1/folder") {
                        setUser("jonas@hinchely.dk", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())
                    val items = mapper.readValue<List<StorageFile>>(response.content!!)
                    assertEquals(3, items.size)
                    log.debug("Received items: $items")
                    assertTrue("a file is contained in response") { items.any { it.path == "/home/user1/folder/a" } }
                    assertTrue("b file is contained in response") { items.any { it.path == "/home/user1/folder/b" } }
                    assertTrue("c file is contained in response") { items.any { it.path == "/home/user1/folder/c" } }
                }
            )
        }
    }

    @Test
    fun listAtPathIncorrectPath() {
        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        StorageServiceDescription.definition(),
                        "localhost",
                        42000
                    )
                    installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                    install(JWTProtection)
                    val fsRoot = createDummyFS()
                    val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                    routing {
                        route("api") {
                            FilesController(fs).configure(this)
                        }
                    }

                },

                test = {
                    val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/notThere") {
                        setUser("jonas@hinchely.dk", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            )
        }
    }

    @Test
    fun missingAuth() {
        withTestApplication(
            moduleFunction = {
                val instance = ServiceInstance(StorageServiceDescription.definition(), "localhost", 42000)
                installDefaultFeatures(mockk(relaxed = true), mockk(relaxed = true), instance, requireJobId = false)
                install(JWTProtection)
                val fsRoot = createDummyFS()
                val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)

                routing {
                    route("api") {
                        FilesController(fs).configure(this)
                    }
                }

            },

            test = {
                val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/user1") {
                    setUser("jonas@hinchely.dk", Role.USER)
                }.response

                assertEquals(HttpStatusCode.Unauthorized, response.status())
            }
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ListAtPathTests::class.java)
    }
}