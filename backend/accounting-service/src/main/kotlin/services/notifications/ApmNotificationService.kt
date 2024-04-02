package dk.sdu.cloud.accounting.services.notifications

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.accounting.api.ApmNotifications
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.accounting.ownersById
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.max

class ApmNotificationService(
    private val accounting: AccountingSystem,
    private val projects: ProjectService,
    private val tokenValidator: TokenValidation<*>,
    private val developmentMode: Boolean,
) {
    private val projectsUpdated = MutableSharedFlow<Project>()

    init {
        projects.addUpdateHandler { projects ->
            projects.forEach { project ->
                println("Project update handler called!")
                projectsUpdated.emit(project)
            }
        }
    }

    private suspend fun retrieveProject(id: String): Project {
        return projects.retrieve(
            ActorAndProject.System,
            ProjectsRetrieveRequest(
                id,
                includeMembers = true,
                includeGroups = true,
                includeSettings = true,
                includePath = true,
            )
        )
    }

    suspend fun handleClient(session: WebSocketServerSession) {
        val incoming = session.incoming
        val frame = incoming.receiveCatching().getOrNull() ?: return
        val buf = frame.buffer

        var providerId = ""
        var replayFrom = 0L
        if (frame.frameType == FrameType.TEXT && developmentMode) {
            // TODO Remove this
            providerId = "k8"
        } else {
            val opcode = buf.get()
            if (opcode != 0.toByte()) return

            replayFrom = buf.getLong()
            val incomingFlags = buf.getLong()

            if (incomingFlags != 0L) return

            val authToken = buf.getString()
            val principal = tokenValidator.validateAndDecodeOrNull(authToken)?.principal ?: return
            if (principal.role != Role.PROVIDER) return
            providerId = principal.username.removePrefix(AuthProviders.PROVIDER_PREFIX)
        }


        var projectCounter = 0
        val projectsInSession = HashMap<String, Pair<Int, Project>>()
        val projectsInverse = HashMap<Int, Project>()
        val projectIsRelevant = HashMap<String, Boolean>()

        var userCounter = 0
        val usersInSession = HashMap<String, Int>()
        val usersInverse = HashMap<Int, String>()

        var categoryCounter = 0
        val categoriesInSession = HashMap<ProductCategoryIdV2, Pair<Int, ProductCategory>>()
        val categoriesInverse = HashMap<Int, ProductCategory>()

        var lastWalletUpdate = replayFrom
        coroutineScope {
            val projectUpdates = Channel<Project>(Channel.BUFFERED)

            val projectsToReplay = projects.findProjectsUpdatedSince(replayFrom)
            launch {
                projectsToReplay.forEach { id ->
                    println("Replay project being sent")
                    projectUpdates.send(retrieveProject(id))
                }

                projectsUpdated
                    .takeWhile { session.isActive }
                    .onEach {
                        println("Sending project update (projects updated)")
                        projectUpdates.send(it)
                    }
                    .collect()
            }

            while (session.isActive) {
                println("Start of cycle")
                val walletBuf: ByteBuffer
                val infoBuf: ByteBuffer
                bufferMutex.withLock {
                    walletBuf = bufferPool.borrow()
                    infoBuf = bufferPool.borrow()
                }

                try {
                    val projectsToSend = HashSet<Int>()
                    val usersToSend = HashSet<Int>()
                    val categoriesToSend = HashSet<Int>()

                    fun registerProjectChange(project: Project): Int {
                        var ref = projectsInSession[project.id]?.first
                        if (ref == null) ref = projectCounter++

                        val existingProject = projectsInSession[project.id]
                        if (existingProject?.second?.modifiedAt != project.modifiedAt) {
                            projectsInSession[project.id] = Pair(ref, project)
                            projectsInverse[ref] = project
                            projectsToSend.add(ref)
                        }
                        return ref
                    }

                    while (true) {
                        val updatedProject = projectUpdates.tryReceive().getOrNull() ?: break
                        println("New project update!")
                        var isRelevant = projectIsRelevant[updatedProject.id]
                        if (isRelevant == null) {
                            val pi = (updatedProject.status.members ?: emptyList()).find { it.role == ProjectRole.PI }
                                ?: error("Could not find PI in $updatedProject")
                            projectIsRelevant[updatedProject.id] = accounting.sendRequest(
                                AccountingRequest.FindRelevantProviders(
                                    IdCard.System,
                                    pi.username,
                                    updatedProject.id,
                                    useProject = true
                                )
                            ).contains(providerId)
                            isRelevant = projectIsRelevant.getValue(updatedProject.id)
                        }

                        if (isRelevant) registerProjectChange(updatedProject)
                    }

                    accounting.sendRequest(
                        AccountingRequest.ForEachUpdatedWallet(
                            IdCard.System,
                            providerId,
                            lastWalletUpdate,
                            handler = { wallet ->
                                val categoryRef = categoriesInSession[wallet.category.toId()]?.first ?: run {
                                    val ref = categoryCounter++
                                    categoriesInSession[wallet.category.toId()] = Pair(ref, wallet.category)
                                    categoriesInverse[ref] = wallet.category
                                    categoriesToSend.add(ref)
                                    ref
                                }

                                val owner = ownersById.getValue(wallet.ownedBy)
                                val ownerIsProject = owner.isProject()
                                val workspaceRef = if (ownerIsProject) {
                                    val existing = projectsInSession[owner.reference]

                                    if (existing == null) {
                                        val project = retrieveProject(owner.reference)
                                        projectIsRelevant[project.id] = true
                                        registerProjectChange(project)
                                    } else {
                                        existing.first
                                    }
                                } else {
                                    val existing = usersInSession[owner.reference]
                                    if (existing == null) {
                                        val ref = userCounter++
                                        usersInSession[owner.reference] = ref
                                        usersInverse[ref] = owner.reference
                                        usersToSend.add(ref)
                                        ref
                                    } else {
                                        existing
                                    }
                                }

                                var flags = 0
                                if (ownerIsProject) flags = flags or 0x2
                                if (wallet.wasLocked) flags = flags or 0x1

                                lastWalletUpdate = max(lastWalletUpdate, wallet.lastSignificantUpdateAt)

                                walletBuf.put(ApmNotifications.OP_WALLET)
                                walletBuf.putInt(workspaceRef)
                                walletBuf.putInt(categoryRef)
                                walletBuf.putLong(wallet.totalActiveQuota())
                                walletBuf.putInt(flags)
                                walletBuf.putLong(wallet.lastSignificantUpdateAt)
                            }
                        )
                    )

                    if (usersToSend.isNotEmpty() || projectsToSend.isNotEmpty() || categoriesToSend.isNotEmpty()) {
                        for (userId in usersToSend) {
                            val user = usersInverse.getValue(userId)
                            infoBuf.put(ApmNotifications.OP_USER_INFO)
                            infoBuf.putInt(userId)
                            infoBuf.putString(user)
                        }

                        for (projectId in projectsToSend) {
                            val project = projectsInverse.getValue(projectId)
                            infoBuf.put(ApmNotifications.OP_PROJECT)
                            infoBuf.putInt(projectId)
                            infoBuf.putLong(project.modifiedAt)
                            infoBuf.putString(defaultMapper.encodeToString(Project.serializer(), project))
                        }

                        for (categoryId in categoriesToSend) {
                            val category = categoriesInverse.getValue(categoryId)
                            infoBuf.put(ApmNotifications.OP_CATEGORY_INFO)
                            infoBuf.putInt(categoryId)
                            infoBuf.putString(defaultMapper.encodeToString(ProductCategory.serializer(), category))
                        }

                        infoBuf.flip()
                        println("Sending info buf ${infoBuf.remaining()}")
                        session.send(Frame.Binary(true, infoBuf))
                        session.flush()
                        println("info buf complete")
                    }

                    walletBuf.flip()
                    if (walletBuf.remaining() > 0) {
                        println("Sending wallet buf ${walletBuf.remaining()}")
                        session.send(Frame.Binary(true, walletBuf))
                        session.flush()
                        println("wallet buf complete")
                    }
                } catch (ex: Throwable) {
                    log.warn("Failed while processing ApmNotifications for $providerId: ${ex.toReadableStacktrace()}")
                } finally {
                    bufferPool.recycle(infoBuf)
                    bufferPool.recycle(walletBuf)
                }
                delay(5_000)
            }
        }
    }

    private fun ByteBuffer.putString(text: String) {
        val encoded = text.encodeToByteArray()
        putInt(encoded.size)
        put(encoded)
    }

    private fun ByteBuffer.getString(): String {
        val size = getInt()
        val bytes = ByteArray(size)
        get(bytes)
        return bytes.decodeToString()
    }

    // TODO(Dan): This might not be enough to not run out of memory in the buffers. If Java only gave us a way of
    //  allocating virtual memory, then this wouldn't be an issue.
    private val bufferPool by lazy { DirectByteBufferPool(4, 1024 * 1024 * 16) }
    private val bufferMutex = Mutex()

    companion object : Loggable {
        override val log = logger()
    }
}
