package dk.sdu.cloud.accounting.services.notifications

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ApmNotifications
import dk.sdu.cloud.accounting.api.ProductCategory
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.accounting.services.accounting.*
import dk.sdu.cloud.accounting.services.accounting.ownersById
import dk.sdu.cloud.accounting.services.projects.v2.ProjectService
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.v2.Project
import dk.sdu.cloud.project.api.v2.ProjectsRetrieveRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidation
import dk.sdu.cloud.service.validateAndDecodeOrNull
import io.ktor.server.websocket.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import kotlin.math.max

class ApmNotificationService(
    private val accounting: AccountingSystem,
    private val projects: ProjectService,
    private val tokenValidator: TokenValidation<*>,
    private val idCards: IdCardService,
    private val developmentMode: Boolean,
) {
    private val projectsUpdated = MutableSharedFlow<Project>()

    init {
        projects.addUpdateHandler { projects, _ ->
            projects.forEach { project ->
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

        val replayFrom: Long
        val providerId: String

        var includeRetiredAmounts = false

        run {
            val frame = incoming.receiveCatching().getOrNull() ?: return
            val buf = frame.buffer

            val opcode = buf.get()
            if (opcode != 0.toByte()) return

            replayFrom = buf.getLong()
            val incomingFlags = buf.getLong()

            includeRetiredAmounts = (incomingFlags and 0x1L) != 0L

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
            val userReplayRequests = Channel<String>(Channel.BUFFERED)

            // Listen for project updates (and replays)
            // ---------------------------------------------------------------------------------------------------------
            val projectsToReplay = projects.findProjectsUpdatedSince(replayFrom)
            launch {
                projectsToReplay.forEach { id ->
                    projectUpdates.send(retrieveProject(id))
                }

                projectsUpdated
                    .takeWhile { session.isActive }
                    .onEach {
                        projectUpdates.send(it)
                    }
                    .collect()
            }

            // Listen for replay requests for users
            // ---------------------------------------------------------------------------------------------------------
            launch {
                while (session.isActive) {
                    val frame = incoming.receiveCatching().getOrNull() ?: break
                    val buf = frame.buffer
                    when (val opcode = buf.get()) {
                        ApmNotifications.OP_REPLAY_USER -> {
                            val username = buf.getString()
                            userReplayRequests.send(username)
                        }

                        else -> {
                            error("Unknown opcode: $opcode")
                        }
                    }
                }
            }

            while (session.isActive) {
                val walletBuf: ByteBuffer
                val infoBuf: ByteBuffer
                bufferMutex.withLock {
                    walletBuf = bufferPool.borrow()
                    infoBuf = bufferPool.borrow()
                }

                var phase = ""
                try {
                    phase = "init"

                    // The information sets we intend to send after this update
                    // -------------------------------------------------------------------------------------------------
                    // NOTE(Dan): Wallets are being written directly into walletBuf
                    val projectsToSend = HashSet<Int>()
                    val usersToSend = HashSet<Int>()
                    val categoriesToSend = HashSet<Int>()

                    // Utilities for populating the information sets. These will look in the session data to determine
                    // if they need to send additional information about them.

                    // NOTE(Dan): registerProjectChange will detect updates based on the modifiedAt timestamp
                    fun registerProjectChange(project: Project, force: Boolean): Int {
                        var ref = projectsInSession[project.id]?.first
                        if (ref == null) ref = projectCounter++

                        val existingProject = projectsInSession[project.id]
                        if (force || existingProject?.second?.modifiedAt != project.modifiedAt) {
                            projectsInSession[project.id] = Pair(ref, project)
                            projectsInverse[ref] = project
                            projectsToSend.add(ref)
                        }
                        return ref
                    }

                    suspend fun registerProjectChangeForId(projectId: String, force: Boolean): Int {
                        return registerProjectChange(retrieveProject(projectId), force)
                    }

                    suspend fun registerOwner(owner: WalletOwner, forceProject: Boolean): Pair<Int, Boolean> {
                        val ownerIsProject = owner is WalletOwner.Project
                        val workspaceRef = if (ownerIsProject) {
                            registerProjectChangeForId(owner.reference(), forceProject)
                        } else {
                            val existing = usersInSession[owner.reference()]
                            if (existing == null) {
                                val ref = userCounter++
                                usersInSession[owner.reference()] = ref
                                usersInverse[ref] = owner.reference()
                                usersToSend.add(ref)
                                ref
                            } else {
                                existing
                            }
                        }
                        return workspaceRef to ownerIsProject
                    }

                    fun registerCategory(category: ProductCategory): Int {
                        return categoriesInSession[category.toId()]?.first ?: run {
                            val ref = categoryCounter++
                            categoriesInSession[category.toId()] = Pair(ref, category)
                            categoriesInverse[ref] = category
                            categoriesToSend.add(ref)
                            ref
                        }
                    }

                    // Project updates
                    // -------------------------------------------------------------------------------------------------
                    phase = "project updates"
                    while (true) {
                        val updatedProject = projectUpdates.tryReceive().getOrNull() ?: break
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

                        if (isRelevant) registerProjectChange(updatedProject, false)
                    }

                    // User replay requests
                    // -------------------------------------------------------------------------------------------------
                    // Providers use this to get updates from a user when they connect. This is required for correct
                    // initialization order at the provider. For example, a provider will receive information about
                    // projects and allocations before the user connects. But the provider needs to initialize project
                    // and allocations after the user has connected.
                    phase = "user replay requests"
                    while (true) {
                        val userToReplay = userReplayRequests.tryReceive().getOrNull() ?: break
                        val card = idCards.fetchIdCard(
                            ActorAndProject(Actor.SystemOnBehalfOfUser(userToReplay), null),
                            allowCached = false
                        ) as IdCard.User

                        val projects = (card.groups.map { idCards.lookupGid(it)?.projectId }.filterNotNull() +
                                card.adminOf.map { idCards.lookupPid(it) }.filterNotNull()).toSet() + null

                        for (project in projects) {
                            val idCard = withRetries(2) {
                                phase = "lookup user card $userToReplay $project $it"
                                idCards.fetchIdCard(
                                    ActorAndProject(Actor.SystemOnBehalfOfUser(userToReplay), project),
                                    allowCached = it == 0,
                                )
                            }

                            val wallets = accounting.sendRequest(AccountingRequest.BrowseWallets(idCard))
                            for (wallet in wallets) {
                                if (wallet.paysFor.provider != providerId) continue

                                val categoryRef = registerCategory(wallet.paysFor)
                                val (workspaceRef, ownerIsProject) = registerOwner(wallet.owner, true)

                                var flags = 0
                                if (ownerIsProject) flags = flags or 0x2
                                if (wallet.maxUsable == 0L) flags = flags or 0x1

                                lastWalletUpdate = max(lastWalletUpdate, wallet.lastSignificantUpdateAt)

                                walletBuf.put(ApmNotifications.OP_WALLET)
                                walletBuf.putInt(workspaceRef)
                                walletBuf.putInt(categoryRef)
                                walletBuf.putLong(wallet.quota)
                                walletBuf.putInt(flags)
                                walletBuf.putLong(wallet.lastSignificantUpdateAt)
                                if (includeRetiredAmounts) {
                                    walletBuf.putLong(wallet.localRetiredUsage)
                                }
                            }
                        }
                        break
                    }

                    // Updated wallets
                    // -------------------------------------------------------------------------------------------------
                    phase = "updated wallets"
                    accounting.sendRequest(
                        AccountingRequest.ForEachUpdatedWallet(
                            IdCard.System,
                            providerId,
                            lastWalletUpdate,
                            handler = { wallet ->
                                val categoryRef = registerCategory(wallet.category)

                                val owner = ownersById.getValue(wallet.ownedBy)
                                val (workspaceRef, ownerIsProject) = registerOwner(owner.toWalletOwner(), false)

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
                                if (includeRetiredAmounts) {
                                    walletBuf.putLong(wallet.localRetiredUsage)
                                }
                            }
                        )
                    )

                    // Send info sets
                    // -------------------------------------------------------------------------------------------------
                    phase = "send info sets"
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
                            println("Sending project $projectId -> ${project.id}")
                            infoBuf.putString(defaultMapper.encodeToString(Project.serializer(), project))
                        }

                        for (categoryId in categoriesToSend) {
                            val category = categoriesInverse.getValue(categoryId)
                            infoBuf.put(ApmNotifications.OP_CATEGORY_INFO)
                            infoBuf.putInt(categoryId)
                            infoBuf.putString(defaultMapper.encodeToString(ProductCategory.serializer(), category))
                        }

                        infoBuf.flip()
                        session.send(Frame.Binary(true, infoBuf))
                        session.flush()
                    }

                    // Send wallet
                    // -------------------------------------------------------------------------------------------------
                    phase = "send wallet"
                    walletBuf.flip()
                    if (walletBuf.remaining() > 0) {
                        session.send(Frame.Binary(true, walletBuf))
                        session.flush()
                    }
                } catch (ex: Throwable) {
                    log.warn("Failed while processing ApmNotifications for $providerId in phase $phase: ${ex.toReadableStacktrace()}")
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

    private val bufferPool by lazy { DirectByteBufferPool(4, 1024 * 1024 * 16) }
    private val bufferMutex = Mutex()

    companion object : Loggable {
        override val log = logger()
    }
}
