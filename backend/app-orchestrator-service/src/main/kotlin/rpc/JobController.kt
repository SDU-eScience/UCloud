package dk.sdu.cloud.app.orchestrator.rpc

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.JobOrchestrator
import dk.sdu.cloud.app.orchestrator.services.JobResourceService2
import dk.sdu.cloud.app.orchestrator.services.ProductCache
import dk.sdu.cloud.app.orchestrator.services.ProviderCommunications
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.server.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.prettyMapper
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class JobController(
    private val db: AsyncDBSessionFactory,
    private val orchestrator: JobOrchestrator,
    private val micro: Micro,
) : Controller {
    val jobs = run {
        val productCache = ProductCache(db)
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        JobResourceService2(
            db,
            ProviderCommunications(micro.backgroundScope, serviceClient, productCache),
            micro.backgroundScope,
            productCache
        )
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

                                        "browse-all", "browse-tokens" -> {
                                            val username = args.getOrNull(0)
                                            val project = args.getOrNull(1)

                                            if (username == null) {
                                                sendMessage("usage: browse-all <username> [project (can be null)]")
                                            } else {
                                                var next: String? = null
                                                while (true) {
                                                    val result = jobs.browse(
                                                        ActorAndProject(
                                                            Actor.SystemOnBehalfOfUser(username),
                                                            project?.takeIf { it != "-" && it != "null" }
                                                        ),
                                                        ResourceBrowseRequest(
                                                            JobIncludeFlags(
                                                                includeParameters = true,
                                                                includeOthers = true
                                                            ),
                                                            itemsPerPage = 1000,
                                                            next
                                                        )
                                                    )

                                                    if (command == "browse-tokens") {
                                                        sendMessage(result.next ?: "no next token")
                                                    } else {
                                                        for (item in result.items) {
                                                            sendMessage(item.id)
                                                        }
                                                    }

//                                                    sendMessage("---")

                                                    next = result.next ?: break
                                                }
                                            }
                                        }

                                        "filter-test" -> {
                                            val username = args.getOrNull(0)
                                            val project = args.getOrNull(1)?.takeIf { it != "-" && it != "null" }
                                            val filterCreatedBy = args.getOrNull(2)

                                            if (username == null || filterCreatedBy == null) {
                                                sendMessage("filter-test: <username> <project?> <filterCreatedBy>")
                                            } else {
                                                var next: String? = null
                                                while (true) {
                                                    val result = jobs.browse(
                                                        ActorAndProject(
                                                            Actor.SystemOnBehalfOfUser(username),
                                                            project,
                                                        ),
                                                        ResourceBrowseRequest(
                                                            JobIncludeFlags(
                                                                includeParameters = true,
                                                                includeOthers = true,
                                                                filterCreatedBy = filterCreatedBy
                                                            ),
                                                            itemsPerPage = 250,
                                                            next
                                                        )
                                                    )

                                                    for (item in result.items) {
                                                        sendMessage("${item.id}: ${item.owner.createdBy}")
                                                    }

                                                    next = result.next ?: break
                                                }
                                            }
                                        }

                                        "hide-test" -> {
                                            val username = args.getOrNull(0)
                                            val project = args.getOrNull(1)?.takeIf { it != "-" && it != "null" }
                                            val hideProductName = args.getOrNull(2)

                                            if (username == null || hideProductName == null) {
                                                sendMessage("hide-test: <username> <project?> <hideCategory>")
                                            } else {
                                                var next: String? = null
                                                while (true) {
                                                    val result = jobs.browse(
                                                        ActorAndProject(
                                                            Actor.SystemOnBehalfOfUser(username),
                                                            project,
                                                        ),
                                                        ResourceBrowseRequest(
                                                            JobIncludeFlags(
                                                                includeParameters = true,
                                                                includeOthers = true,
                                                                hideProductId = hideProductName,
                                                            ),
                                                            itemsPerPage = 250,
                                                            next
                                                        )
                                                    )

                                                    for (item in result.items) {
                                                        sendMessage("${item.id}: ${item.owner.createdBy}")
                                                    }

                                                    next = result.next ?: break
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

                                        "acl-test" -> {
                                            val id = args.getOrNull(0)
                                            val username = args.getOrNull(1)
                                            val target = args.getOrNull(2)
                                            val type = args.getOrNull(3)

                                            if (id == null || username == null || target == null || type == null) {
                                                sendMessage("Usage: acl-test <id> <username> <target> <type> <perms>")
                                            } else {
                                                val added = if (type == "add") {
                                                    listOf(ResourceAclEntry(AclEntity.User(target), listOf(Permission.READ, Permission.EDIT)))
                                                } else {
                                                    emptyList()
                                                }

                                                val deleted = if (type == "deleted") {
                                                    listOf(AclEntity.User(target))
                                                } else {
                                                    emptyList()
                                                }

                                                jobs.updateAcl(
                                                    ActorAndProject(Actor.SystemOnBehalfOfUser(username), null),
                                                    bulkRequestOf(
                                                        UpdatedAcl(
                                                            id,
                                                            added,
                                                            deleted
                                                        )
                                                    ).also {
                                                        sendMessage(prettyMapper.encodeToString(it))
                                                    }
                                                )
                                            }
                                        }

                                        else -> {
                                            sendMessage("unknown command")
                                        }
                                    }
                                } catch (ex: Throwable) {
                                    sendMessage(ex.stackTraceToString().toString())
//                                    sendMessage(ex.toReadableStacktrace().toString())
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

        implement(Jobs.updateAcl) {
            jobs.updateAcl(actorAndProject, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.terminate) {
            jobs.terminate(actorAndProject, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.follow) {
            jobs.follow(this)
            ok(JobsFollowResponse(emptyList(), emptyList()))
        }

        implement(Jobs.extend) {
            jobs.extend(actorAndProject, request)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.suspend) {
            jobs.suspendOrUnsuspendJob(actorAndProject, request, shouldSuspend = true)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.unsuspend) {
            jobs.suspendOrUnsuspendJob(actorAndProject, request, shouldSuspend = false)
            ok(BulkResponse(request.items.map { }))
        }

        implement(Jobs.openInteractiveSession) {
            ok(jobs.openInteractiveSession(actorAndProject, request))
        }

        implement(Jobs.retrieveProducts) {
            ok(jobs.retrieveProducts(actorAndProject))
        }

        implement(Jobs.retrieveUtilization) {
            ok(jobs.retrieveUtilization(actorAndProject, request))
        }

        implement(Jobs.search) {
            ok(jobs.search(actorAndProject, request))
        }

        implement(Jobs.init) {
            ok(jobs.initializeProviders(actorAndProject))
        }

        implement(JobsControl.register) {
            ok(jobs.register(actorAndProject, request))
        }

        // Old implementation below this line
        // ==================================

        val userApi = orchestrator.userApi()
        val controlApi = orchestrator.controlApi()
        implement(controlApi.chargeCredits) {
            ok(orchestrator.chargeCredits(actorAndProject, request))
        }

        implement(controlApi.checkCredits) {
            ok(orchestrator.chargeCredits(actorAndProject, request, checkOnly = true))
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
