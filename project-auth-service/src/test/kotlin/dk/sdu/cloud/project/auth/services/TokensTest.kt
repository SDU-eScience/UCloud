package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.TestCallResult
import dk.sdu.cloud.service.test.assertThatInstance
import dk.sdu.cloud.service.test.assertThatProperty
import dk.sdu.cloud.service.test.initializeMicro
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokensTest {
    private lateinit var micro: Micro
    private lateinit var db: DBSessionFactory<HibernateSession>
    private lateinit var tokenDao: AuthTokenDao<HibernateSession>
    private lateinit var tokenInvalidator: TokenInvalidator<HibernateSession>
    private lateinit var tokenRefresher: TokenRefresher<HibernateSession>

    private val projectA = "MyProjectA"
    private val projectB = "MyProjectB"

    @BeforeTest
    fun beforeTest() {
        micro = initializeMicro().apply {
            install(HibernateFeature)
        }

        db = micro.hibernateDatabase
        tokenDao = AuthTokenHibernateDao()
        tokenInvalidator = TokenInvalidator(ClientMock.authenticatedClient, db, tokenDao)
        tokenRefresher = TokenRefresher(ClientMock.authenticatedClient, db, tokenDao, tokenInvalidator)


        db.withTransaction { session ->
            ProjectRole.values().forEach { role ->
                tokenDao.storeToken(session, AuthToken("token-$projectA-$role", projectA, role))
                tokenDao.storeToken(session, AuthToken("token-$projectB-$role", projectB, role))
            }
        }
    }

    private fun assertTokensExistForProject(projectName: String) {
        val tokens = db.withTransaction { tokenDao.tokensForProject(it, projectName) }
        assertThatProperty(tokens, { it.size }) { it == ProjectRole.values().size }
    }

    private fun assertTokensDontExistForProject(projectName: String) {
        try {
            db.withTransaction { tokenDao.tokensForProject(it, projectName) }
            assert(false)
        } catch (ex: AuthTokenException.NotFound) {
            // Tokens don't exist. Good.
        }
    }

    @Test
    fun `test simple invalidation of project tokens`() = runBlocking {
        ClientMock.mockCallSuccess(AuthDescriptions.bulkInvalidate, Unit)

        assertTokensExistForProject(projectA)
        assertTokensExistForProject(projectB)

        tokenInvalidator.invalidateTokensForProject(projectA)
        assertTokensDontExistForProject(projectA)
        assertTokensExistForProject(projectB)

        tokenInvalidator.invalidateTokensForProject(projectB)
        assertTokensDontExistForProject(projectA)
        assertTokensDontExistForProject(projectB)
    }

    @Test(expected = AuthTokenException.NotFound::class)
    fun `test double invalidation`() = runBlocking {
        ClientMock.mockCallSuccess(AuthDescriptions.bulkInvalidate, Unit)
        tokenInvalidator.invalidateTokensForProject(projectA)
        tokenInvalidator.invalidateTokensForProject(projectA)
    }

    @Test
    fun `test external invalidation`() = runBlocking {
        val authRefreshStatus = HttpStatusCode.Forbidden
        var didCallInvalidate = false

        ClientMock.mockCallError(
            AuthDescriptions.refresh,
            statusCode = authRefreshStatus
        )

        ClientMock.mockCall(
            AuthDescriptions.bulkInvalidate,
            {
                didCallInvalidate = true
                TestCallResult.Ok(Unit)
            }
        )

        ClientMock.mockCall(
            ProjectDescriptions.viewMemberInProject,
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )

        assertThatProperty(
            instance = runCatching {
                tokenRefresher.refreshTokenForUser("myuser", ClientMock.authenticatedClient, projectA)
            }.exceptionOrNull(),
            property = { it },
            matcher = { it is RPCException && it.httpStatusCode == authRefreshStatus }
        )

        assertTrue(didCallInvalidate)
    }

    @Test
    fun `test token refresh`() = runBlocking {
        val accessToken = "token"
        ClientMock.mockCallSuccess(
            AuthDescriptions.refresh,
            AccessToken(accessToken)
        )

        ClientMock.mockCall(
            ProjectDescriptions.viewMemberInProject,
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )


        ClientMock.mockCallSuccess(
            AuthDescriptions.tokenExtension,
            TokenExtensionResponse(accessToken, null, null)
        )

        val result = tokenRefresher.refreshTokenForUser("myuser", ClientMock.authenticatedClient, projectA)
        assertEquals(accessToken, result.accessToken)
    }

    @Test
    fun `test token refresh (bad project)`() = runBlocking {
        ClientMock.mockCall(
            ProjectDescriptions.viewMemberInProject,
            { TestCallResult.Error(error = null, statusCode = HttpStatusCode.NotFound) }
        )

        val exception = runCatching {
            tokenRefresher.refreshTokenForUser(
                "myuser",
                ClientMock.authenticatedClient,
                projectA
            )
        }.exceptionOrNull()
        assertThatInstance(exception) { it is RPCException && it.httpStatusCode == HttpStatusCode.NotFound }
    }

    @Test
    fun `test token refresh (bad auth service)`() = runBlocking {
        ClientMock.mockCall(
            ProjectDescriptions.viewMemberInProject,
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )

        ClientMock.mockCall(
            AuthDescriptions.refresh,
            { TestCallResult.Error(error = null, statusCode = HttpStatusCode.InternalServerError) }
        )

        val exception = runCatching {
            tokenRefresher.refreshTokenForUser(
                "myuser",
                ClientMock.authenticatedClient,
                projectA
            )
        }.exceptionOrNull()
        assertThatInstance(exception) { it is RPCException && it.httpStatusCode == HttpStatusCode.InternalServerError }
    }

    @Test(expected = AuthTokenException.NotFound::class)
    fun `test looking for bad token`() = runBlocking {
        db.withTransaction { session ->
            tokenDao.retrieveTokenForProjectInRole(session, "notfound", ProjectRole.ADMIN)
        }

        Unit
    }

    @Test(expected = AuthTokenException.NotFound::class)
    fun `test invalidation of bad project`() = runBlocking {
        db.withTransaction { session ->
            tokenDao.invalidateTokensForProject(session, "notfound")
        }
    }
}
