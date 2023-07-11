package dk.sdu.cloud

import dk.sdu.cloud.Simulator.Companion.terminalApplication
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.ApplicationType
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.*
import org.jetbrains.kotlin.backend.common.push
import java.io.File
import kotlin.collections.ArrayList
import kotlin.random.Random

class Simulator(
    private val serviceClient: AuthenticatedClient,
    private val numberOfUsers: Int,
) {
    private val users = ArrayList<SimulatedUser>()
    private val projects = ArrayList<Project>()

    private suspend fun initializeUsers() {
        // Look up users
        val userFile = File("simulated_users")

        if (!userFile.exists()) {
            userFile.createNewFile()
        }

        log.debug("User file path: ${userFile.absolutePath}")
        val existingUsers = userFile.readLines().filter { it.isNotEmpty() }

        val selectedUsers = if (existingUsers.size > numberOfUsers) {
            existingUsers.subList(0, numberOfUsers - 1)
        } else {
            existingUsers
        }

        log.debug("Reusing ${selectedUsers.size} existing simulated users")
        for (existingUser in selectedUsers) {
            val (username, password, refreshToken) = existingUser.split(" ")
            val simUser = SimulatedUser(serviceClient)
            simUser.initialize(username, password, refreshToken)
            users.add(simUser)
        }

        // Find existing projects
        val foundProjects = Projects.browse.call(
            ProjectsBrowseRequest(250, includeMembers = true),
            serviceClient
        ).orThrow().items.filter { project ->
            project.status.members?.any { users.map { sim -> sim.username }.contains(it.username) } ?: false
        }

        log.debug("Found ${foundProjects.size} existing projects:")
        for (project in foundProjects) {
            log.debug("  ${project.specification.title}: ${project.status.members?.size}")
        }

        projects.addAll(foundProjects)

        if (existingUsers.size < numberOfUsers) {
            log.debug("Creating ${numberOfUsers - existingUsers.size} new simulated users")
            val newUsers = ArrayList<SimulatedUser>()
            var missingCount = numberOfUsers - existingUsers.size

            while (missingCount > 0) {
                val newUser = SimulatedUser(serviceClient)
                newUser.initialize()
                newUsers.push(newUser)
                missingCount--
            }

            log.debug("Writing new users to file")
            for (user in newUsers) {
                userFile.appendText("${user.username} ${user.password} ${user.refreshToken}\n")
            }

            log.debug("Assigning users to projects")
            for (user in newUsers) {
                val roll = Random.nextInt(100)

                // If this is the first user, create a project else create project 5% of the time.
                if (users.isEmpty() || roll < 5) {
                    projects.add(user.createProject())
                } else {
                    // Join project with the least members
                    val chosenProject = projects.sortedBy { it.status.members?.size }.first()
                    val projectPiName = Projects.retrieve.call(
                        ProjectsRetrieveRequest(chosenProject.id, includeMembers = true),
                        serviceClient
                    ).orThrow().status.members?.first { it.role == ProjectRole.PI }?.username
                    val simPi = users.first { it.username == projectPiName }
                    user.joinProject(chosenProject.id, simPi)
                }

                users.add(user)
            }
        }
    }

    fun start() {
        log.debug("Starting load simulation")

        runBlocking {
            initializeUsers()

            users.forEach { user ->
                launch {
                    user.simulate()
                }
            }
        }
    }

    companion object : Loggable {
        val computeByHours = ProductCategoryId("cpu", "k8")
        val storageByQuota = ProductCategoryId("storage", "k8")

        val terminalApplication = NameAndVersion("terminal-ubuntu", "0.17.0")

        override val log = logger()
    }
}

private fun ProductCategoryId.toReference(): ProductReference {
    return ProductReference("$name-1", name, provider)
}

class SimulatedUser(
    private val serviceClient: AuthenticatedClient,
) {
    lateinit var username: String
    lateinit var password: String
    lateinit var refreshToken: String
    lateinit var client: AuthenticatedClient
    lateinit var wsClient: AuthenticatedClient
    lateinit var project: Project

    suspend fun initialize(username: String? = null, password: String? = null, refreshToken: String? = null) {
        this.refreshToken = if (refreshToken != null) {
            this.username = username ?: ""
            this.password = password ?: ""
            refreshToken
        } else {
            this.username = "simulated-user-${Time.now()}"
            this.password = generatePassword()
            log.debug("Creating user ${this.username}")

            UserDescriptions.createNewUser.call(
                listOf(
                    CreateSingleUserRequest(
                        this.username,
                        this.password,
                        "${username}@localhost.direct",
                        Role.USER,
                        "First",
                        "Last"
                    )
                ),
                serviceClient
            ).orThrow().single().refreshToken
        }

        client = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Normal(this.refreshToken, OutgoingHttpCall)
        ).authenticateClient(OutgoingHttpCall)

        wsClient = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Normal(this.refreshToken, OutgoingHttpCall)
        ).authenticateClient(OutgoingWSCall)

        val projects = Projects.browse.call(
            ProjectsBrowseRequest(),
            client
        ).orThrow().items

        if (projects.isNotEmpty()) {
            project = projects.first()
            client = client.withProject(project.id)
        }
    }

    suspend fun createProject(): Project {
        val title = "simulated-project-${Time.now()}"

        val existingProjects = Projects.browse.call(
            ProjectsBrowseRequest(itemsPerPage = 250),
            serviceClient
        ).orThrow().items

        val parent = existingProjects.find { it.specification.title == "Provider k8" }!!
        val projectId = Projects.create.call(
            bulkRequestOf(
                Project.Specification(
                    parent = parent.id,
                    title = title
                )
            ),
            serviceClient
        ).orThrow().responses.single().id

        Projects.createInvite.call(
            bulkRequestOf(ProjectsCreateInviteRequestItem(username)),
            serviceClient.withProject(projectId)
        ).orThrow()

        Projects.acceptInvite.call(
            bulkRequestOf(FindByProjectId(projectId)),
            client.withProject(projectId)
        ).orThrow()

        Projects.changeRole.call(
            bulkRequestOf(ProjectsChangeRoleRequestItem(username, ProjectRole.PI)),
            serviceClient.withProject(projectId)
        )

        val allocations = Wallets.retrieveWalletsInternal.call(
            WalletsInternalRetrieveRequest(WalletOwner.Project(parent.id)),
            serviceClient
        ).orThrow().wallets.flatMap { it.allocations }

        Accounting.deposit.call(
            bulkRequestOf(
                allocations.map { alloc ->
                    DepositToWalletRequestItem(
                        WalletOwner.Project(projectId),
                        alloc.id,
                        10_000_000_000L,
                        "wallet init",
                        grantedIn = null
                    )
                }
            ),
            serviceClient
        ).orThrow()

        val project = Projects.retrieve.call(
            ProjectsRetrieveRequest(projectId),
            client
        ).orThrow()

        this.project = project
        client = client.withProject(projectId)

        Simulator.log.debug("Created project ${project.specification.title}")

        return project
    }

    suspend fun joinProject(projectId: String, pi: SimulatedUser): Project {
        Projects.createInvite.call(
            bulkRequestOf(ProjectsCreateInviteRequestItem(username)),
            pi.client.withProject(projectId)
        ).orThrow()

        Projects.acceptInvite.call(
            bulkRequestOf(FindByProjectId(projectId)),
            client.withProject(projectId)
        ).orThrow()

        val joinedProject = Projects.retrieve.call(
            ProjectsRetrieveRequest(projectId),
            client.withProject(projectId)
        ).orThrow()

        this.project = joinedProject
        client = client.withProject(projectId)

        log.debug("User $username joined ${this.project.specification.title}")

        return joinedProject
    }


    // Do:
    //  - Launch terminal app and open terminal
    //  - Launch visual app and launch interface
    //  - Copy some files around (maybe)?

    suspend fun simulate() {
        while (true) {
            val delayMillis = Random.nextLong(2000, 5000)
            delay(delayMillis)

            doStep()
        }
    }

    private suspend fun doStep() {
        fun chance(chance: Int): Boolean {
            val diceRoll = Random.nextInt(100)
            return diceRoll <= chance
        }

        when {
            chance(10) -> doAction(Action.CreateFolder)
            chance(10) -> doAction(Action.MoveFolder)
            chance(10) -> doAction(Action.OpenInterface)
            chance(10) -> doAction(Action.OpenTerminal)
            chance(10) -> doAction(Action.BrowseJobs)
            chance(10) -> doAction(Action.FollowJob)
            chance(10) -> doAction(Action.StartJob)
            chance(1) -> doAction(Action.StopJob)
            else -> {
                // Do nothing
            }
        }
    }

    private suspend fun browseJobs(): List<Job> {
        return Jobs.browse.call(
            ResourceBrowseRequest(JobIncludeFlags()),
            client
        ).orThrow().items
    }

    private suspend fun browseFileCollections(): List<FileCollection> {
        return FileCollections.browse.call(
            ResourceBrowseRequest(FileCollectionIncludeFlags()),
            client
        ).orThrow().items
    }

    enum class Action {
        CreateFolder,
        MoveFolder,
        BrowseJobs,
        StartJob,
        StopJob,
        FollowJob,
        OpenInterface,
        OpenTerminal
    }

    private suspend fun doAction(action: Action) {
        when (action) {
            Action.CreateFolder -> {
                log.debug("$username Creating folder")

                val drive = browseFileCollections().firstOrNull()

                if (drive != null) {
                    var files = Files.browse.call(
                        ResourceBrowseRequest(UFileIncludeFlags(path = "/${drive.id}")),
                        client
                    ).orThrow().items.filter { !it.id.endsWith("Jobs") }

                    if (files.size < 10) {
                        Files.createFolder.call(
                            bulkRequestOf(
                                FilesCreateFolderRequestItem(
                                    "/${drive.id}/${generatePassword()}",
                                    WriteConflictPolicy.REPLACE
                                )
                            ),
                            client
                        ).orThrow()
                    }
                }
            }

            Action.MoveFolder -> {
                val drive = browseFileCollections().firstOrNull()

                if (drive != null) {
                    val file = Files.browse.call(
                        ResourceBrowseRequest(UFileIncludeFlags(path = "/${drive.id}")),
                        client
                    ).orThrow().items.firstOrNull { !it.id.endsWith("Jobs") }

                    if (file != null) {
                        val newId = "/${drive.id}/${generatePassword()}"
                        log.debug("$username Moving ${file.id} to $newId")

                        Files.move.call(
                            bulkRequestOf(FilesMoveRequestItem(file.id, newId, WriteConflictPolicy.REPLACE)),
                            client
                        ).orThrow()
                    }
                }
            }

            Action.BrowseJobs -> {
                val jobs = browseJobs()
                log.debug("$username Browsed ${jobs.size} jobs")
            }

            Action.StartJob -> {
                val jobs = browseJobs()
                val running = jobs.filter { it.status.state == JobState.RUNNING && it.owner.createdBy == username }

                if (running.size < 2) {
                    log.debug("$username Starting job")
                    Jobs.create.call(
                        bulkRequestOf(
                            JobSpecification(
                                terminalApplication,
                                Simulator.computeByHours.toReference(),
                                parameters = emptyMap(),
                                resources = emptyList(),
                                timeAllocation = SimpleDuration(1, 0, 0)
                            ),
                        ),
                        client
                    ).orThrow()
                }
            }

            Action.StopJob -> {
                val job = browseJobs().firstOrNull {
                    it.status.state == JobState.RUNNING && it.owner.createdBy == username
                }

                if (job != null) {
                    log.debug("$username Stopping job ${job.id}")
                    Jobs.terminate.call(
                        bulkRequestOf(FindByStringId(job.id)),
                        client
                    ).orThrow()
                }
            }

            Action.OpenInterface -> {
                log.debug("$username Open interface")
                val job = browseJobs().firstOrNull {
                    it.owner.createdBy == username &&
                        it.status.state == JobState.RUNNING &&
                        it.status.resolvedApplication?.invocation?.applicationType == ApplicationType.WEB
                }

                if (job != null) {
                    Jobs.openInteractiveSession.call(
                        bulkRequestOf(JobsOpenInteractiveSessionRequestItem(job.id, 0, InteractiveSessionType.WEB)),
                        client
                    ).orThrow()
                }
            }

            Action.FollowJob -> {
                val jobs = browseJobs()
                val running = jobs.firstOrNull { it.status.state == JobState.RUNNING }

                if (running != null) {
                    log.debug("$username Follow job ${running.id}")
                    coroutineScope {
                        val job = launch {
                            try {
                                Jobs.follow.subscribe(
                                    FindByStringId(running.id),
                                    wsClient,
                                    handler = { _ -> }
                                ).orThrow()
                            } catch (ex: RPCException) {
                                if (ex.httpStatusCode.value == 499) {
                                    // Ignore
                                } else {
                                    throw ex
                                }
                            }
                        }

                        delay(Random.nextLong(5_000, 10_000))

                        runCatching {
                            job.cancel()
                        }

                        runCatching { job.join() }
                    }
                }
            }

            Action.OpenTerminal -> {
                log.debug("$username Open terminal")
                val job = browseJobs().firstOrNull {
                    it.owner.createdBy == username && it.status.state == JobState.RUNNING
                }

                if (job != null) {
                    Jobs.openInteractiveSession.call(
                        bulkRequestOf(JobsOpenInteractiveSessionRequestItem(job.id, 0, InteractiveSessionType.SHELL)),
                        client
                    ).orThrow()
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }

    private fun generatePassword(): String {
        val pool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..30).map { Random.nextInt(0, pool.size).let { pool[it] } }.joinToString("")
    }
}
