package dk.sdu.cloud.file.http

import dk.sdu.cloud.auth.api.validateAndClaim
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.services.WithBackgroundScope
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Test
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DownloadTests : WithBackgroundScope() {
    @Test
    fun downloadFileTest() {
        withKtorTest(
            setup = { configureWithDownloadController() },

            test = {
                val token = TokenValidationMock.createTokenForPrincipal(TestUsers.user)
                val response = sendRequest(
                    HttpMethod.Get,
                    "/api/files/download?path=/home/user/folder/a&token=$token",
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

    private fun KtorApplicationTestSetupContext.configureWithDownloadController(
        additional: ((FileControllerContext) -> Unit)? = null
    ): List<Controller> {
        return configureServerWithFileController(backgroundScope) {
            val tokenValidation = micro.tokenValidation as TokenValidationJWT
            val jwt = tokenValidation.validate(TokenValidationMock.createTokenForPrincipal(TestUsers.user))
            mockkStatic("dk.sdu.cloud.auth.api.ValidationUtilsKt")
            coEvery { tokenValidation.validateAndClaim(any(), any(), any()) } returns jwt
            additional?.invoke(it)
            listOf(
                SimpleDownloadController(
                    it.authenticatedClient,
                    CommandRunnerFactoryForCalls(it.runner, mockk(relaxed = true)),
                    it.coreFs,
                    tokenValidation,
                    it.lookupService
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
                        "/api/files/download?path=/home/user/folder&token=$token",
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
                    "/api/files/download?path=/home/user/folder/notThere/a&token=$token",
                    TestUsers.user
                ).response

                assertEquals(HttpStatusCode.NotFound, response.status())
            }
        )
    }

    @Test
    fun `test download sensitive file`() {
        withKtorTest(
            setup = {
                configureWithDownloadController(additional = {
                    it.runner.withBlockingContext("user") { ctx ->
                        it.coreFs.setSensitivityLevel(
                            ctx,
                            "/home/user/folder/a",
                            SensitivityLevel.SENSITIVE
                        )
                    }
                })
            },
            test = {
                val token = TokenValidationMock.createTokenForPrincipal(TestUsers.user)
                val response = sendRequest(
                    HttpMethod.Get,
                    "/api/files/download?path=/home/user/folder/a&token=$token",
                    TestUsers.user
                ).response

                assertEquals(HttpStatusCode.Forbidden, response.status())
            }
        )
    }

    @Test
    fun `test download private folder with sensitive content`() {
        withKtorTest(
            setup = {
                configureWithDownloadController(additional = {
                    it.runner.withBlockingContext("user") { ctx ->
                        it.coreFs.setSensitivityLevel(ctx, "/home/user/folder", SensitivityLevel.PRIVATE)
                        it.coreFs.setSensitivityLevel(
                            ctx,
                            "/home/user/folder/a",
                            SensitivityLevel.SENSITIVE
                        )
                    }
                })
            },
            test = {
                val token = TokenValidationMock.createTokenForPrincipal(TestUsers.user)
                val response = sendRequest(
                    HttpMethod.Get,
                    "/api/files/download?path=/home/user/folder&token=$token",
                    TestUsers.user
                ).response

                val zis = ZipInputStream(response.byteContent?.inputStream())

                var curEntry = zis.nextEntry
                while (curEntry != null) {
                    // Folder 'a' is sensitive
                    assertNotEquals("a", curEntry.name)
                    curEntry = zis.nextEntry
                }
            }
        )
    }

}

