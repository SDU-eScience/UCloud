package dk.sdu.cloud.storage.http.download

import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.ServiceInstance
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.definition
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.http.FilesController
import dk.sdu.cloud.storage.http.SimpleDownloadController
import dk.sdu.cloud.storage.http.files.setUser
import dk.sdu.cloud.storage.services.cephFSWithRelaxedMocks
import dk.sdu.cloud.storage.services.createDummyFS
import dk.sdu.cloud.storage.util.mockedUser
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.install
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.header
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.junit.Test
import kotlin.test.assertEquals

class DownloadTests {
    @Test
    fun downloadFileTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = mockedUser()
                        coEvery { TokenValidation.validateAndClaim(any(), any(), any()) } returns user

                        val instance = ServiceInstance(
                            dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                            "localhost",
                            42000
                        )
                        val cloud: AuthenticatedCloud = mockk(relaxed = true)
                        installDefaultFeatures(cloud, mockk(relaxed = true), instance, requireJobId = false)
                        install(JWTProtection)

                        val fsRoot = createDummyFS()
                        val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)
                        routing {
                            route("api") {
                                SimpleDownloadController(cloud, fs, mockk(relaxed = true)).configure(this)
                            }
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/files/download?path=/home/user1/folder/a&token=token") {
                                setUser("schulz", Role.USER)
                            }.response

                        println(response.headers.allValues())
                        assertEquals(HttpStatusCode.OK, response.status())
                        assertEquals("""[attachment; filename="a"]""", response.headers.values("Content-Disposition").toString())
                        assertEquals("[6]", response.headers.values("Content-Length").toString())
                    }
                )
            }
        }
    }

    @Test
    fun downloadFolderTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = mockedUser()
                        coEvery { TokenValidation.validateAndClaim(any(), any(), any()) } returns user

                        val instance = ServiceInstance(
                            dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                            "localhost",
                            42000
                        )
                        val cloud: AuthenticatedCloud = mockk(relaxed = true)
                        installDefaultFeatures(cloud, mockk(relaxed = true), instance, requireJobId = false)
                        install(JWTProtection)

                        val fsRoot = createDummyFS()
                        val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)
                        routing {
                            route("api") {
                                SimpleDownloadController(cloud, fs, mockk(relaxed = true)).configure(this)
                            }
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/files/download?path=/home/user1/folder&token=token") {
                                setUser("schulz", Role.USER)
                            }.response


                        assertEquals(HttpStatusCode.OK, response.status())
                        assertEquals("""[attachment; filename="folder.zip"]""", response.headers.values("Content-Disposition").toString())
                    }
                )
            }
        }
    }

    @Test
    fun downloadNonexistingPathTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = mockedUser()
                        coEvery { TokenValidation.validateAndClaim(any(), any(), any()) } returns user

                        val instance = ServiceInstance(
                            dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                            "localhost",
                            42000
                        )
                        val cloud: AuthenticatedCloud = mockk(relaxed = true)
                        installDefaultFeatures(cloud, mockk(relaxed = true), instance, requireJobId = false)
                        install(JWTProtection)

                        val fsRoot = createDummyFS()
                        val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)
                        routing {
                            route("api") {
                                SimpleDownloadController(cloud, fs, mockk(relaxed = true)).configure(this)
                            }
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/files/download?path=/home/user1/folder/notThere/a&token=token") {
                                setUser("schulz", Role.USER)
                            }.response


                        assertEquals(HttpStatusCode.BadRequest, response.status())
                    }
                )
            }
        }
    }

    @Test
    fun downloadBulkFilesTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = mockedUser()
                        coEvery { TokenValidation.validateAndClaim(any(), any(), any()) } returns user

                        val instance = ServiceInstance(
                            dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                            "localhost",
                            42000
                        )
                        val cloud: AuthenticatedCloud = mockk(relaxed = true)
                        installDefaultFeatures(cloud, mockk(relaxed = true), instance, requireJobId = false)
                        install(JWTProtection)

                        val fsRoot = createDummyFS()
                        val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)
                        routing {
                            route("api") {
                                SimpleDownloadController(cloud, fs, mockk(relaxed = true)).configure(this)
                            }
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/files/bulk") {
                                setUser("schulz", Role.USER)
                                setBody("""
                                    {
                                    "prefix" : "/home/user1/folder/",
                                    "files" : ["a", "b", "c"]
                                    }
                                    """.trimIndent())
                            }.response

                        println(response.headers.allValues())
                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                )
            }
        }
    }

    @Test
    fun downloadBulkFilesWithSingleMissingFileTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = {
                        val user = mockedUser()
                        coEvery { TokenValidation.validateAndClaim(any(), any(), any()) } returns user

                        val instance = ServiceInstance(
                            dk.sdu.cloud.storage.api.StorageServiceDescription.definition(),
                            "localhost",
                            42000
                        )
                        val cloud: AuthenticatedCloud = mockk(relaxed = true)
                        installDefaultFeatures(cloud, mockk(relaxed = true), instance, requireJobId = false)
                        install(JWTProtection)

                        val fsRoot = createDummyFS()
                        val fs = cephFSWithRelaxedMocks(fsRoot.absolutePath)
                        routing {
                            route("api") {
                                SimpleDownloadController(cloud, fs, mockk(relaxed = true)).configure(this)
                            }
                        }
                    },

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/files/bulk") {
                                setUser("schulz", Role.USER)
                                setBody("""
                                    {
                                    "prefix" : "/home/user1/folder/",
                                    "files" : ["a", "b", "c", "d"]
                                    }
                                    """.trimIndent())
                            }.response

                        //Should be OK since non existing files are filtered away.
                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                )
            }
        }
    }
}

