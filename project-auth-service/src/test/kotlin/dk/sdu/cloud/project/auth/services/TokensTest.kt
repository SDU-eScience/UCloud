package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.auth.api.AccessToken
import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.TokenExtensionResponse
import dk.sdu.cloud.project.api.ProjectDescriptions
import dk.sdu.cloud.project.api.ProjectMember
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.ViewMemberInProjectResponse
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.authenticatedCloud
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.install
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
        tokenInvalidator = TokenInvalidator(micro.authenticatedCloud, db, tokenDao)
        tokenRefresher = TokenRefresher(micro.authenticatedCloud, db, tokenDao, tokenInvalidator)


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
        CloudMock.mockCallSuccess(AuthDescriptions, { AuthDescriptions.bulkInvalidate }, Unit)

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
        CloudMock.mockCallSuccess(AuthDescriptions, { AuthDescriptions.bulkInvalidate }, Unit)
        tokenInvalidator.invalidateTokensForProject(projectA)
        tokenInvalidator.invalidateTokensForProject(projectA)
    }

    @Test
    fun `test external invalidation`() = runBlocking {
        val authRefreshStatus = HttpStatusCode.Forbidden
        var didCallInvalidate = false

        CloudMock.mockCallError(
            AuthDescriptions,
            { AuthDescriptions.refresh },
            statusCode = authRefreshStatus
        )

        CloudMock.mockCall(
            AuthDescriptions,
            { AuthDescriptions.bulkInvalidate },
            {
                didCallInvalidate = true
                TestCallResult.Ok(Unit)
            }
        )

        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )

        assertThatProperty(
            instance = runCatching {
                tokenRefresher.refreshTokenForUser("myuser", micro.authenticatedCloud, projectA)
            }.exceptionOrNull(),
            property = { it },
            matcher = { it is RPCException && it.httpStatusCode == authRefreshStatus }
        )

        assertTrue(didCallInvalidate)
    }

    @Test
    fun `test token refresh`() = runBlocking {
        val accessToken = "token"
        CloudMock.mockCallSuccess(
            AuthDescriptions,
            { AuthDescriptions.refresh },
            AccessToken(accessToken)
        )

        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )


        CloudMock.mockCallSuccess(
            AuthDescriptions,
            { AuthDescriptions.tokenExtension },
            TokenExtensionResponse(accessToken, null, null)
        )

        val result = tokenRefresher.refreshTokenForUser("myuser", micro.authenticatedCloud, projectA)
        assertEquals(accessToken, result.accessToken)
    }

    @Test
    fun `test token refresh (bad project)`() = runBlocking {
        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Error(error = null, statusCode = HttpStatusCode.NotFound) }
        )

        val exception = runCatching {
            tokenRefresher.refreshTokenForUser(
                "myuser",
                micro.authenticatedCloud,
                projectA
            )
        }.exceptionOrNull()
        assertThatInstance(exception) { it is RPCException && it.httpStatusCode == HttpStatusCode.NotFound }
    }

    @Test
    fun `test token refresh (bad auth service)`() = runBlocking {
        CloudMock.mockCall(
            ProjectDescriptions,
            { ProjectDescriptions.viewMemberInProject },
            { TestCallResult.Ok(ViewMemberInProjectResponse(ProjectMember(it.username, ProjectRole.USER))) }
        )

        CloudMock.mockCall(
            AuthDescriptions,
            { AuthDescriptions.refresh },
            { TestCallResult.Error(error = null, statusCode = HttpStatusCode.InternalServerError) }
        )

        val exception = runCatching {
            tokenRefresher.refreshTokenForUser(
                "myuser",
                micro.authenticatedCloud,
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
