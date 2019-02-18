package dk.sdu.cloud.file.http.download

import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.file.api.BulkDownloadRequest
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.http.files.configureServerWithFileController
import dk.sdu.cloud.file.services.BulkDownloadService
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockkStatic
import org.junit.Test
import kotlin.test.assertEquals

class DownloadTests {
    @Test
    fun downloadFileTest() {
        withKtorTest(
            setup = { configureWithDownloadController() },

            test = {
                val token = TokenValidationMock.createTokenForPrincipal(TestUsers.user)
                val response = sendRequest(
                    HttpMethod.Get,
                    "/api/files/download?path=/home/user1/folder/a&token=$token",
                    TestUsers.user
                ).response

                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    "attachment; filename=\"a\"",
                    response.headers.values(HttpHeaders.ContentDisposition).single()
                )

                assertEquals(
                    6,
                    response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                )
            }
        )
    }

    private fun KtorApplicationTestSetupContext.configureWithDownloadController(): List<Controller> {
        return configureServerWithFileController {
            val tokenValidation = micro.tokenValidation as TokenValidationJWT
            val jwt = tokenValidation.validate(TokenValidationMock.createTokenForPrincipal(TestUsers.user))
            mockkStatic("dk.sdu.cloud.auth.api.ValidationUtilsKt")
            coEvery { tokenValidation.validateAndClaim(any(), any(), any()) } returns jwt
            val bulk = BulkDownloadService(it.coreFs)
            listOf(
                SimpleDownloadController(
                    it.authenticatedClient,
                    it.runner,
                    it.coreFs,
                    bulk,
                    tokenValidation
                )
            )
        }
    }

    @Test
    fun downloadFolderTest() {
        withKtorTest(
            setup = { configureWithDownloadController() },
            test = {
                val token = TokenValidationMock.createTokenForPrincipal(TestUsers.user)
                val response =
                    sendRequest(
                        HttpMethod.Get,
                        "/api/files/download?path=/home/user1/folder&token=$token",
                        TestUsers.user
                    ).response


                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(
                    """[attachment; filename="folder.zip"]""",
                    response.headers.values("Content-Disposition").toString()
                )
            }
        )
    }

    @Test
    fun downloadNonexistingPathTest() {
        withKtorTest(
            setup = { configureWithDownloadController() },
            test = {
                val token = TokenValidationMock.createTokenForPrincipal(TestUsers.user)
                val response = sendRequest(
                    HttpMethod.Get,
                    "/api/files/download?path=/home/user1/folder/notThere/a&token=$token",
                    TestUsers.user
                ).response

                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        )
    }

    @Test
    fun downloadBulkFilesTest() {
        withKtorTest(
            setup = { configureWithDownloadController() },
            test = {
                val response = sendJson(
                    HttpMethod.Post,
                    "/api/files/bulk",
                    BulkDownloadRequest("/home/user1/folder/", listOf("a", "b", "c")),
                    TestUsers.user
                ).response

                println(response.headers.allValues())
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }

    @Test
    fun downloadBulkFilesWithSingleMissingFileTest() {
        withKtorTest(
            setup = { configureWithDownloadController() },
            test = {
                val response = sendJson(
                    HttpMethod.Post,
                    "/api/files/bulk",
                    BulkDownloadRequest("/home/user1/folder/", listOf("a", "b", "c", "d")),
                    TestUsers.user
                ).response

                //Should be OK since non existing files are filtered away.
                assertEquals(HttpStatusCode.OK, response.status())
            }
        )
    }
}

