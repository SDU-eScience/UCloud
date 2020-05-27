package dk.sdu.cloud.project

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.project.rpc.GroupController
import dk.sdu.cloud.project.rpc.MembershipController
import dk.sdu.cloud.project.rpc.ProjectController
import dk.sdu.cloud.project.services.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import project.rpc.FavoritesController
import kotlin.system.exitProcess

class Server(
    override val micro: Micro,
    private val configuration: Configuration
) : CommonServer {
    override val log = logger()

    override fun start() {
        val eventStreamService = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val db = AsyncDBSessionFactory(micro.databaseConfig)

        val eventProducer = eventStreamService.createProducer(ProjectEvents.events)

        val projects = ProjectService(client, eventProducer)
        val groups = GroupService(projects, eventProducer)
        val favorites = FavoriteService()
        val queries = QueryService(projects)

        if (micro.commandLineArguments.contains("--remind")) {
            try {
                runBlocking<Nothing> {
                    val verificationReminder = VerificationReminder(
                        db,
                        queries,
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
                ProjectController(db, projects, queries, configuration),
                GroupController(db, groups, queries),
                MembershipController(db, queries),
                FavoritesController(db, favorites)
            )
        }

        startServices()
    }
}
