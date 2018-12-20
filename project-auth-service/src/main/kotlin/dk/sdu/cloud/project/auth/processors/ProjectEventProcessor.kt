package dk.sdu.cloud.project.auth.processors

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateUserRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.api.usernameForProjectInRole
import dk.sdu.cloud.project.auth.services.AuthToken
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.UserTokenDao
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.consumeAndCommit
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.orThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

class ProjectEventProcessor<DBSession>(
    private val serviceCloud: AuthenticatedCloud,
    private val db: DBSessionFactory<DBSession>,
    private val authTokenDao: AuthTokenDao<DBSession>,
    private val userTokenDao: UserTokenDao<DBSession>,
    private val eventConsumerFactory: EventConsumerFactory,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        eventConsumerFactory.createConsumer(ProjectEvents.events).configure { root ->
            root.consumeAndCommit { (_, event) ->
                val projectId = event.project.id

                db.withTransaction { session ->
                    runBlocking {
                        when (event) {
                            is ProjectEvent.Created -> {
                                val tokens = ProjectRole.values().map { role ->
                                    async {
                                        role to UserDescriptions.createNewUser.call(
                                            CreateUserRequest(
                                                usernameForProjectInRole(projectId, role),
                                                "", // Interface should also accept null as input
                                                Role.PROJECT_PROXY
                                            ),
                                            serviceCloud
                                        ).orThrow().refreshToken
                                    }
                                }.awaitAll()

                                // TODO Endpoint would ideally be a bulk create
                                // TODO Delete any that were created in case of a crash

                                tokens.forEach { (role, token) ->
                                    authTokenDao.storeToken(session, AuthToken(token, projectId, role))
                                }
                            }

                            is ProjectEvent.Deleted -> {
                                authTokenDao.invalidateTokensForProject(session, projectId)
                                userTokenDao.invalidateTokensForProject(session, projectId)
                            }

                            is ProjectEvent.MemberDeleted -> {
                                userTokenDao.invalidateTokensForUser(session, event.projectMember.username)
                            }

                            is ProjectEvent.MemberAdded, is ProjectEvent.MemberRoleUpdated -> {
                                // Do nothing
                            }
                        }
                    }
                }
            }
        }
    }
}
