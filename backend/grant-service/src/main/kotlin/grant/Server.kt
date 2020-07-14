package dk.sdu.cloud.grant 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.grant.rpc.GiftController
import dk.sdu.cloud.grant.rpc.GrantController
import dk.sdu.cloud.grant.services.*
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val projects = ProjectCache(serviceClient)
        val settings = SettingsService(projects)
        val templates = TemplateService(projects, settings, "Default")
        val notifications = NotificationService(projects, serviceClient)
        val applications = ApplicationService(projects, settings, notifications, serviceClient)
        val comments = CommentService(applications, notifications)
        val gifts = GiftService(projects, serviceClient)

        with(micro.server) {
            configureControllers(
                GrantController(applications, comments, settings, templates, serviceClient, db),
                GiftController(gifts, db)
            )
        }
        
        startServices()
    }
}