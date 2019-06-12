package dk.sdu.cloud.project.auth

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.project.auth.http.ProjectAuthController
import dk.sdu.cloud.project.auth.processors.ProjectAuthEventProcessor
import dk.sdu.cloud.project.auth.processors.ProjectEventProcessor
import dk.sdu.cloud.project.auth.services.AuthTokenDao
import dk.sdu.cloud.project.auth.services.AuthTokenHibernateDao
import dk.sdu.cloud.project.auth.services.StorageInitializer
import dk.sdu.cloud.project.auth.services.TokenInvalidator
import dk.sdu.cloud.project.auth.services.TokenRefresher
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        // Initialize services here
        val db = micro.hibernateDatabase
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val tokenDao: AuthTokenDao<HibernateSession> = AuthTokenHibernateDao()
        val tokenInvalidator = TokenInvalidator(client, db, tokenDao)
        val tokenRefresher = TokenRefresher(client, db, tokenDao, tokenInvalidator)

        val storageInitializer = StorageInitializer(
            refreshTokenCloudFactory = { refreshToken ->
                RefreshingJWTAuthenticator(
                    micro.client,
                    refreshToken,
                    micro.tokenValidation as TokenValidationJWT
                ).authenticateClient(OutgoingHttpCall)
            }
        )

        // Initialize consumers here:
        ProjectEventProcessor(
            client,
            db,
            tokenDao,
            tokenInvalidator,
            micro.eventStreamService
        ).init()

        ProjectAuthEventProcessor(
            db,
            tokenDao,
            micro.eventStreamService
        ).apply {
            addListener(storageInitializer)

            init()
        }

        // Initialize server
        with(micro.server) {
            configureControllers(
                ProjectAuthController(tokenRefresher, client)
            )
        }

        startServices()
    }
}
