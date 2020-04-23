package dk.sdu.cloud.project

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.rpc.GroupController
import dk.sdu.cloud.project.rpc.MembershipController
import dk.sdu.cloud.project.rpc.ProjectController
import dk.sdu.cloud.project.services.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.H2_DIALECT
import dk.sdu.cloud.service.db.H2_DRIVER
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        val eventStreamService = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val db = AsyncDBSessionFactory(micro.databaseConfig)

        val projectDao = ProjectDao()
        val groupDao = GroupDao()
        val eventProducer = eventStreamService.createProducer(ProjectEvents.events)

        val projectService = ProjectService(
            db,
            projectDao,
            eventProducer,
            client
        )

        val groupService = GroupService(
            db,
            groupDao,
            projectDao,
            eventProducer,
            micro.authenticator.authenticateClient(OutgoingHttpCall)
        )

        val membershipService = MembershipService(db, groupDao, projectDao)

        if (micro.commandLineArguments.contains("--remind")) {
            try {
                runBlocking<Nothing> {
                    val verificationReminder = VerificationReminder(
                        db,
                        projectDao,
                        MailCooldownDao(),
                        client
                    )

                    verificationReminder.sendReminders()
                    exitProcess(0)
                }
            } catch (ex: Throwable) {
                log.error(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        with(micro.server) {
            configureControllers(
                ProjectController(projectService),
                GroupController(groupService),
                MembershipController(membershipService)
            )
        }

        startServices()
    }
}
