package dk.sdu.cloud.storage.http

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.services.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.services.createDummyFS
import dk.sdu.cloud.storage.util.AuthMocking
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
import kotlin.test.assertEquals


fun TestApplicationRequest.setUser(username: String = "user", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer $username/$role")
}

class ImplementFeatureTest {

    val auth = AuthMocking()

    @Test
    fun listAtPathTest() {
        auth.withAuthMock {
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
                    val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/user1/folder") {
                        setUser("jonas@hinchely.dk", Role.ADMIN)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                }
            )
        }
    }

    @Test
    fun listAtPathIncorrectPath() {
        auth.withAuthMock {
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
                    val response = handleRequest(HttpMethod.Get, "/api/files?path=/home/notThere") {
                        setUser("jonas@hinchely.dk", Role.ADMIN)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())
                }
            )
        }
    }

}