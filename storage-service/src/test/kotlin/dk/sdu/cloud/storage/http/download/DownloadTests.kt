package dk.sdu.cloud.storage.http.download

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.storage.http.SimpleDownloadController
import dk.sdu.cloud.storage.http.files.configureServerWithFileController
import dk.sdu.cloud.storage.http.files.setUser
import dk.sdu.cloud.storage.services.BulkDownloadService
import dk.sdu.cloud.storage.util.mockedUser
import dk.sdu.cloud.storage.util.withAuthMock
import io.ktor.application.Application
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.staticMockk
import io.mockk.use
import org.junit.Test
import kotlin.test.assertEquals

class DownloadTests {
    @Test
    fun downloadFileTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {

                withTestApplication(
                    moduleFunction = configureWithDownloadController(),

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/files/download?path=/home/user1/folder/a&token=token") {
                                setUser("User", Role.USER)
                            }.response

                        println(response.headers.allValues())
                        assertEquals(HttpStatusCode.OK, response.status())
                        assertEquals(
                            """[attachment; filename="a"]""",
                            response.headers.values("Content-Disposition").toString()
                        )
                        assertEquals("[6]", response.headers.values("Content-Length").toString())
                    }
                )
            }
        }
    }

    private fun configureWithDownloadController(): Application.() -> Unit {
        return {
            configureServerWithFileController {
                val user = mockedUser()
                coEvery { TokenValidation.validateAndClaim(any(), any(), any()) } returns user

                val bulk = BulkDownloadService(it.coreFs)
                configureControllers(SimpleDownloadController(it.cloud, it.runner, it.coreFs, bulk))
            }
        }
    }

    @Test
    fun downloadFolderTest() {
        staticMockk("dk.sdu.cloud.auth.api.ValidationUtilsKt").use {
            withAuthMock {
                withTestApplication(
                    moduleFunction = configureWithDownloadController(),

                    test = {
                        val response =
                            handleRequest(HttpMethod.Get, "/api/files/download?path=/home/user1/folder&token=token") {
                                setUser("User", Role.USER)
                            }.response


                        assertEquals(HttpStatusCode.OK, response.status())
                        assertEquals(
                            """[attachment; filename="folder.zip"]""",
                            response.headers.values("Content-Disposition").toString()
                        )
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
                    moduleFunction = configureWithDownloadController(),

                    test = {
                        val response =
                            handleRequest(
                                HttpMethod.Get,
                                "/api/files/download?path=/home/user1/folder/notThere/a&token=token"
                            ) {
                                setUser("User", Role.USER)
                            }.response


                        assertEquals(HttpStatusCode.NotFound, response.status())
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
                    moduleFunction = configureWithDownloadController(),

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/files/bulk") {
                                setUser("User", Role.USER)
                                setBody(
                                    """
                                    {
                                    "prefix" : "/home/user1/folder/",
                                    "files" : ["a", "b", "c"]
                                    }
                                    """.trimIndent()
                                )
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
                    moduleFunction = configureWithDownloadController(),

                    test = {
                        val response =
                            handleRequest(HttpMethod.Post, "/api/files/bulk") {
                                setUser("User", Role.USER)
                                setBody(
                                    """
                                    {
                                    "prefix" : "/home/user1/folder/",
                                    "files" : ["a", "b", "c", "d"]
                                    }
                                    """.trimIndent()
                                )
                            }.response

                        //Should be OK since non existing files are filtered away.
                        assertEquals(HttpStatusCode.OK, response.status())

                    }
                )
            }
        }
    }
}

