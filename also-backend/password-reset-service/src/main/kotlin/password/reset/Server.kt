package dk.sdu.cloud.password.reset

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.password.reset.rpc.*
import dk.sdu.cloud.password.reset.services.PasswordResetService
import dk.sdu.cloud.password.reset.services.ResetRequestsAsyncDao
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import java.security.SecureRandom

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val resetRequestsDao = ResetRequestsAsyncDao()
        val secureRandom = SecureRandom()
        val passwordResetService = PasswordResetService(
            db,
            authenticatedClient,
            resetRequestsDao,
            secureRandom
        )

        with(micro.server) {
            configureControllers(
                PasswordResetController(passwordResetService)
            )
        }

        startServices()
    }

}
