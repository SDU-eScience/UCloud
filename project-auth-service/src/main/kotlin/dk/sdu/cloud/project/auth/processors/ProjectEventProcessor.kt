package dk.sdu.cloud.project.auth.processors

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.kafka.MappedEventProducer
import dk.sdu.cloud.project.api.ProjectEvent
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.auth.api.ProjectAuthEvent
import dk.sdu.cloud.project.auth.api.usernameForProjectInRole
import dk.sdu.cloud.project.auth.services.AuthToken
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.consumeAndCommit
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger

class ProjectEventProcessor<DBSession>(
    private val serviceCloud: AuthenticatedClient,
    private val db: DBSessionFactory<DBSession>,
    private val authTokenDao: AuthTokenDao<DBSession>,
    private val tokenInvalidator: TokenInvalidator<DBSession>,
    private val eventConsumerFactory: EventConsumerFactory,
    private val eventProducer: MappedEventProducer<*, ProjectAuthEvent>,
    private val parallelism: Int = 4
) {
    fun init(): List<EventConsumer<*>> = (0 until parallelism).map { _ ->
        eventConsumerFactory.createConsumer(ProjectEvents.events).configure { root ->
            root.consumeAndCommit { (_, event) ->
                val projectId = event.project.id

                runBlocking {
                    log.debug("Receiving event: $event")
                    when (event) {
                        is ProjectEvent.Created -> {
                            val createUserRequest = ProjectRole.values().map { role ->
                                CreateSingleUserRequest(
                                    username = usernameForProjectInRole(projectId, role),
                                    password = null,
                                    role = Role.PROJECT_PROXY
                                )
                            }

                            val tokens = UserDescriptions.createNewUser.call(
                                createUserRequest,
                                serviceCloud
                            ).orThrow()

                            val rolesAndTokens = ProjectRole.values().zip(tokens)
                            db.withTransaction { session ->
                                rolesAndTokens.forEach { (role, token) ->
                                    authTokenDao.storeToken(session, AuthToken(token.refreshToken, projectId, role))
                                }
                            }

                            eventProducer.emit(ProjectAuthEvent.Initialized(projectId))
                        }

                        is ProjectEvent.Deleted -> {
                            tokenInvalidator.invalidateTokensForProject(projectId)
                        }

                        is ProjectEvent.MemberDeleted, is ProjectEvent.MemberAdded,
                        is ProjectEvent.MemberRoleUpdated -> {
                            // Do nothing
                        }
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
