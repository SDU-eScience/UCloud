package dk.sdu.cloud.password.reset

import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.password.reset.rpc.*
import dk.sdu.cloud.password.reset.services.PasswordResetService
import dk.sdu.cloud.password.reset.services.ResetRequestsHibernateDao

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = micro.hibernateDatabase

        val resetRequestsDao = ResetRequestsHibernateDao()
        val passwordResetService = PasswordResetService(db, resetRequestsDao)
        with(micro.server) {
            configureControllers(
                PasswordResetController(passwordResetService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
