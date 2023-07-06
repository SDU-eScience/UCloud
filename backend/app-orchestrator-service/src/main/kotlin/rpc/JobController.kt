package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.accounting.util.SimpleProviderCommunication
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.orchestrator.services.JobResourceService2
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.configuration
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.micro.requestChunkOrNull
import dk.sdu.cloud.prettyMapper
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class JobController(
    private val db: DBContext,
    private val orchestrator: JobOrchestrator,
    private val micro: Micro,
) : Controller {
    val jobs = run {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        JobResourceService2(db, Providers(serviceClient) { comms -> comms })
    }
    @OptIn(DelicateCoroutinesApi::class)
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        if (micro.developmentModeEnabled) {
            GlobalScope.launch {
                delay(1000)
                val appEngine = micro.feature(ServerFeature).ktorApplicationEngine!!
                val devKey = micro.configuration.requestChunkOrNull<String>("jobsDevKey")
                if (devKey != null) {
                    appEngine.application.routing {
                        webSocket("/$devKey") {
                            suspend fun sendMessage(message: String) {
                                message.lines().forEach {
                                    if (it.isNotBlank()) outgoing.send(Frame.Text(it))
                                }
                            }

                            sendMessage("Ready to accept queries!")

                            for (frame in incoming) {
                                try {
                                    if (frame !is Frame.Text) continue

                                    val text = frame.readText().trim()
                                    val split = text.split(" ")
                                    val command = split.firstOrNull()
                                    val args = split.drop(1)
                                    when (command) {
                                        "retrieve" -> {
                                            val id = args.getOrNull(0)
                                            val username = args.getOrNull(1)
                                            val project = args.getOrNull(2)

                                            if (id == null || username == null) {
                                                sendMessage("Usage: retrieve <id> <username> [project]")
                                            } else {
                                                val result = jobs.retrieve(
                                                    ActorAndProject(Actor.SystemOnBehalfOfUser(username), project),
                                                    ResourceRetrieveRequest(
                                                        JobIncludeFlags(
                                                            includeParameters = true,
                                                            includeOthers = true
                                                        ), id
                                                    )
                                                )

                                                if (result == null) {
                                                    sendMessage("Unknown job")
                                                } else {
                                                    sendMessage(
                                                        prettyMapper.encodeToString(
                                                            Job.serializer(),
                                                            result
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        "browse-simple", "browse" -> {
                                            val username = args.getOrNull(0)
                                            val project = args.getOrNull(1)
                                            val next = args.getOrNull(2)

                                            if (username == null) {
                                                sendMessage("usage: browse <username> [project (can be null)] [next]")
                                            } else {
                                                val result = jobs.browse(
                                                    ActorAndProject(Actor.SystemOnBehalfOfUser(username), project?.takeIf { it != "-" && it != "null" }),
                                                    ResourceBrowseRequest(
                                                        JobIncludeFlags(
                                                            includeParameters = true,
                                                            includeOthers = true
                                                        ),
                                                        itemsPerPage = 250,
                                                        next
                                                    )
                                                )

                                                if (command == "browse-simple") {
                                                    sendMessage(result.next ?: "no next token")
                                                    for (item in result.items) {
                                                        sendMessage(item.id)
                                                    }
                                                } else {
                                                    sendMessage(
                                                        prettyMapper.encodeToString(
                                                            PageV2.serializer(Job.serializer()),
                                                            result
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        "update" -> {
                                            val id = args.getOrNull(0)
                                            val username = args.getOrNull(1)
                                            val project = args.getOrNull(2)?.takeIf { it != "-" && it != "null" }
                                            val message = args.getOrNull(3)
                                            if (username == null || id == null) {
                                                sendMessage("Usage: update <username> <project (can be null)> <id> [message]")
                                            } else {
                                                sendMessage("Ready to receive update!")
                                                val updateText = (incoming.receive() as Frame.Text).readText()

                                                val request = bulkRequestOf(
                                                    ResourceUpdateAndId(
                                                        id,
                                                        defaultMapper.decodeFromString(
                                                            JobUpdate.serializer(),
                                                            updateText
                                                        ).also { it.status = message }
                                                    )
                                                )

                                                jobs.addUpdate(
                                                    ActorAndProject(Actor.SystemOnBehalfOfUser(username), project),
                                                    request
                                                )

                                                sendMessage("Updating $id with:")
                                                sendMessage(prettyMapper.encodeToString(request))
                                            }
                                        }

                                        else -> {
                                            sendMessage("unknown command")
                                        }
                                    }
                                } catch (ex: Throwable) {
                                    sendMessage(ex.toReadableStacktrace().toString())
                                }
                            }
                        }

                        get("/api/overheadTest") {
                            call.respondBytes(ByteArray(0))
                        }
                    }
                }
            }
        }

        implement(Jobs.browse) {
            ok(jobs.browse(actorAndProject, request))
//                (ctx as HttpCall).call.respondBytesWriter {
//                    jobs.browseV2(actorAndProject, request, this)
//                }
//                okContentAlreadyDelivered()
        }

        implement(Jobs.retrieve) {
            ok(jobs.retrieve(actorAndProject, request) ?: throw RPCException("Unknown job", HttpStatusCode.NotFound))
        }

        implement(Jobs.create) {
            ok(jobs.create(actorAndProject, request))
        }

        implement(JobsControl.browse) {
            ok(jobs.browse(actorAndProject, request))
        }

        implement(JobsControl.retrieve) {
            ok(jobs.retrieve(actorAndProject, request) ?: throw RPCException("Unknown job", HttpStatusCode.NotFound))
        }

        implement(JobsControl.update) {
            ok(jobs.addUpdate(actorAndProject, request))
        }

        // Old implementation below this line
        // ==================================

        val userApi = orchestrator.userApi()
        val controlApi = orchestrator.controlApi()
        implement(userApi.retrieveProducts) {
            ok(orchestrator.retrieveProducts(actorAndProject))
        }

        implement(userApi.updateAcl) {
            ok(orchestrator.updateAcl(actorAndProject, request))
        }

        implement(userApi.init) {
            ok(orchestrator.init(actorAndProject))
        }

        implement(controlApi.chargeCredits) {
            ok(orchestrator.chargeCredits(actorAndProject, request))
        }

        implement(controlApi.checkCredits) {
            ok(orchestrator.chargeCredits(actorAndProject, request, checkOnly = true))
        }

        implement(controlApi.register) {
            ok(orchestrator.register(actorAndProject, request))
        }

        implement(userApi.search) {
            ok(orchestrator.search(actorAndProject, request))
        }

        implement(Jobs.terminate) {
            ok(orchestrator.terminate(actorAndProject, request))
        }

        implement(Jobs.follow) {
            orchestrator.follow(this)
            ok(JobsFollowResponse(emptyList(), emptyList()))
        }

        implement(Jobs.extend) {
            ok(orchestrator.extendDuration(actorAndProject, request))
        }

        implement(Jobs.suspend) {
            ok(orchestrator.suspendJob(actorAndProject, request))
        }

        implement(Jobs.unsuspend) {
            ok(orchestrator.unsuspendJob(actorAndProject, request))
        }

        implement(Jobs.openInteractiveSession) {
            ok(orchestrator.openInteractiveSession(actorAndProject, request))
        }

        implement(Jobs.retrieveUtilization) {
            ok(orchestrator.retrieveUtilization(actorAndProject, request))
        }
    }

    private fun CallHandler<*, *, *>.verifySlaFromPrincipal() {
        val principal = ctx.securityPrincipal
        if (principal.role == Role.USER && !principal.twoFactorAuthentication &&
            principal.principalType == "password"
        ) {
            throw RPCException(
                "2FA must be activated before application services are available",
                HttpStatusCode.Forbidden
            )
        }

        if (principal.role in Roles.END_USER && !principal.serviceAgreementAccepted) {
            throw RPCException("Service license agreement not yet accepted", HttpStatusCode.Forbidden)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
