package dk.sdu.cloud.mail

import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.mail.api.Mail
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.mail.rpc.*
import dk.sdu.cloud.mail.services.MailService
import dk.sdu.cloud.mail.services.SettingsService
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(private val config: MailConfiguration, override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val db = AsyncDBSessionFactory(micro)

        val settingsService = SettingsService(db)

        val mailService = MailService(
            authenticatedClient,
            config.fromAddress,
            config.whitelist,
            micro.developmentModeEnabled || config.useFakeSend,
            db,
            settingsService
        )

        if (micro.commandLineArguments.contains("--send-test-mail")) {
            try {
                val principal = SecurityPrincipal("_password-reset", Role.SERVICE, "", "")
                runBlocking {
                    mailService.send(principal, "support@imada.sdu.dk", Mail.NewCommentOnApplicationMail("Test", "Testing...", "test"), true, true)
                }
            } finally {
                exitProcess(0)
            }
        }
        with(micro.server) {
            configureControllers(
                MailController(mailService, settingsService, db)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
