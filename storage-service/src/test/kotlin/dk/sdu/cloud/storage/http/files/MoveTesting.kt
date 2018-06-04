package dk.sdu.cloud.storage.http.files

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.definition
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.services.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.services.createDummyFS
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class MoveTesting {

    @Test
    fun moveFileTest() {

        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
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
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(
                        HttpMethod.Post,
                        "/api/files/move?path=/home/user1/folder/a&newPath=/home/user1"
                    ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response3.status())

                    val response4 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response4.status())
                }
            )
        }
    }

    @Test
    fun moveFileToSameDirectoryTest() {

        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
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
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(
                        HttpMethod.Post,
                        "/api/files/move?path=/home/user1/folder/a&newPath=/home/user1/folder/"
                    ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.BadRequest, response2.status())

                }
            )
        }
    }

    @Test
    fun moveFileToNonexisitingDirectoryTest() {

        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
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
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(
                        HttpMethod.Post,
                        "/api/files/move?path=/home/user1/folder/a&newPath=/home/user1/folder/notThere/a"
                    ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.BadRequest, response2.status())

                }
            )
        }
    }

    @Test
    fun moveFileAndRename() {

        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
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
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response.status())

                    val response2 = handleRequest(
                        HttpMethod.Post,
                        "/api/files/move?path=/home/user1/folder/a&newPath=/home/user1/folder/newName"
                    ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response2.status())

                    val response3 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/a") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response3.status())

                    val response4 = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/newName") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.OK, response4.status())

                }
            )
        }
    }

    @Test
    fun moveNonexistingFile() {

        withAuthMock {
            withTestApplication(
                moduleFunction = {
                    val instance = ServiceInstance(
                        dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
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
                    val response = handleRequest(HttpMethod.Get, "/api/files/stat?path=/home/user1/folder/k") {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.NotFound, response.status())

                    val response2 = handleRequest(
                        HttpMethod.Post,
                        "/api/files/move?path=/home/user1/folder/k&newPath=/home/user1/another-one/k"
                    ) {
                        setUser("user1", Role.USER)
                    }.response

                    assertEquals(HttpStatusCode.BadRequest, response2.status())

                }
            )
        }
    }
}