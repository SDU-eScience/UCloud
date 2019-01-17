package dk.sdu.cloud.project.auth.http

import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.project.auth.api.FetchTokenRequest
import dk.sdu.cloud.project.auth.api.FetchTokenResponse
import dk.sdu.cloud.project.auth.services.AuthToken
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.AuthTokenHibernateDao
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.cloudContext
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.KtorApplicationTestContext
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.parseSuccessful
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpTest {
    private lateinit var db: DBSessionFactory<HibernateSession>
    private lateinit var tokenDao: AuthTokenDao<HibernateSession>
    private lateinit var tokenInvalidator: TokenInvalidator<HibernateSession>
    private lateinit var tokenRefresher: TokenRefresher<HibernateSession>

    private val projectA = "MyProjectA"
    private val projectB = "MyProjectB"

    private fun runTest(test: KtorApplicationTestContext.() -> Unit) {
        withKtorTest(
            microConfigure = {
                install(HibernateFeature)
            },

            setup = {
                db = micro.hibernateDatabase
                tokenDao = AuthTokenHibernateDao()
                tokenInvalidator = TokenInvalidator(micro.authenticatedCloud, db, tokenDao)
                tokenRefresher = TokenRefresher(micro.authenticatedCloud, db, tokenDao, tokenInvalidator)


                db.withTransaction { session ->
                    ProjectRole.values().forEach { role ->
                        tokenDao.storeToken(session, AuthToken("token-$projectA-$role", projectA, role))
                        tokenDao.storeToken(session, AuthToken("token-$projectB-$role", projectB, role))
                    }
                }

                listOf(ProjectAuthController(tokenRefresher, micro.cloudContext))
            },

            test = test
        )
    }

    @Test
    fun `test fetching a token`() = runTest {
        val token = "token"
        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )

        CloudMock.mockCallSuccess(
            AuthDescriptions,
            { AuthDescriptions.refresh },
            AccessToken(token)
        )

        val response = sendJson(
            HttpMethod.Post,
            "/api/projects/auth",
            FetchTokenRequest(projectA),
            TestUsers.user
        )

        response.assertSuccess()
        val parsedResponse = response.parseSuccessful<FetchTokenResponse>()
        assertEquals(token, parsedResponse.accessToken)
    }

    @Test
    fun `test fetching a token (no user)`() = runTest {
        val token = "token"
        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )

        CloudMock.mockCallSuccess(
            AuthDescriptions,
            { AuthDescriptions.refresh },
            AccessToken(token)
        )

        val response = sendJson(
            HttpMethod.Post,
            "/api/projects/auth",
            FetchTokenRequest(projectA),
            null
        )

        response.assertStatus(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `test fetching a token (no project)`() = runTest {
        val token = "token"
        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Error(error = null, statusCode = HttpStatusCode.NotFound) }
        )

        CloudMock.mockCallSuccess(
            AuthDescriptions,
            { AuthDescriptions.refresh },
            AccessToken(token)
        )

        val response = sendJson(
            HttpMethod.Post,
            "/api/projects/auth",
            FetchTokenRequest("notfound"),
            TestUsers.user
        )

        response.assertStatus(HttpStatusCode.NotFound)
    }
}
