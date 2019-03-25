package dk.sdu.cloud.project

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.http.ProjectController
import dk.sdu.cloud.project.services.ProjectHibernateDao
import dk.sdu.cloud.project.services.ProjectService
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val eventStreamService = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val projectDao = ProjectHibernateDao()
        val projectService = ProjectService(
            micro.hibernateDatabase,
            projectDao,
            eventStreamService.createProducer(ProjectEvents.events),
            client
        )

        with(micro.server) {
            configureControllers(
                ProjectController(projectService)
            )
        }

        startServices()
    }
}
