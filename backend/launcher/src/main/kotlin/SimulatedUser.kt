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

        log.debug(userFile.absolutePath)
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
        log.debug("Started load simulation")

        runBlocking {
            initializeUsers()
        }

        log.debug("Initialized:")
        log.debug(" - Users: ${users.size}")
        for (u in users) {
            log.debug("   - ${u.username}: ${u.project.specification.title}")
        }

        log.debug(" - Projects: ${projects.size}")

        for (p in projects) {
            log.debug("   - ${p.id}: ${p.specification.title}")
        }

        // Start simulation
        runBlocking {
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

enum class SimulatedUserState {
    Normal,
    Following
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

    val jobs = ArrayList<JobSpecification>()
    var state: SimulatedUserState = SimulatedUserState.Normal

    suspend fun initialize(username: String? = null, password: String? = null, refreshToken: String? = null) {
        this.refreshToken = if (refreshToken != null) {
            this.username = username ?: ""
            this.password = password ?: ""
            refreshToken
        } else {
            if (username.isNullOrBlank()) {
                this.username = "simulated-user-${Time.now()}"
                this.password = generatePassword()
                log.debug("Creating user ${this.username}")

                UserDescriptions.createNewUser.call(
                    listOf(
                        CreateSingleUserRequest(
                            this.username,
                            this.password,
                            "${username}@localhost",
                            Role.USER,
                            "First",
                            "Last"
                        )
                    ),
                    serviceClient
                ).orThrow().single().refreshToken
            } else {
                // TODO(Brian): Login using password does not seem to work currently

                /*log.debug("Trying to log in as ${this.username}")

                val login = AuthDescriptions.passwordLogin.call(
                    Unit,
                    serviceClient.withHttpBody(
                        """
                            -----boundary
                            Content-Disposition: form-data; name="service"

                            dev-web
                            -----boundary
                            Content-Disposition: form-data; name="username"

                            user
                            -----boundary
                            Content-Disposition: form-data; name="password"

                            mypassword
                            -----boundary--
                        """.trimIndent(),
                        ContentType.MultiPart.FormData.withParameter("boundary", "---boundary")
                    )
                ).ctx as OutgoingHttpCall

                val response = login.response!!
                log.debug("$response")
                log.debug("${response.headers}")
                log.debug("${HttpHeaders.SetCookie}")
                val cookiesSet = response.headers[HttpHeaders.SetCookie]!!
                log.debug("${cookiesSet}")

                val split = cookiesSet.split(";").associate {
                    val key = it.substringBefore('=')
                    val value = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                    key to value
                }

                log.debug("$split")

                split["refreshToken"]!!*/
                ""
            }
        }

        log.debug("${this.username} logged in with token: ${this.refreshToken}")

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

        Simulator.log.debug("Created project ${project.id}: ${project.specification.title}")

        return project
    }

    suspend fun joinProject(projectId: String, pi: SimulatedUser): Project {
        log.debug("User $username is joining project ${projectId}")

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
        println("Started simulating $username")
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
            chance(10) -> doAction(Action.OpenInterface)
            chance(10) -> doAction(Action.OpenTerminal)
            chance(1) && chance(10) -> doAction(Action.StopJob)
            chance(10) -> doAction(Action.BrowseJobs)
            chance(10) -> doAction(Action.FollowJob)
            chance(10) -> doAction(Action.StartJob)
            else -> {
                // Do nothing
            }
        }
    }

    suspend fun browseJobs(): List<Job> {
        return Jobs.browse.call(
            ResourceBrowseRequest(JobIncludeFlags()),
            client
        ).orThrow().items
    }

    enum class Action {
        BrowseJobs,
        StartJob,
        StopJob,
        FollowJob,
        OpenInterface,
        OpenTerminal
    }

    suspend fun doAction(action: Action) {
        when (action) {
            Action.BrowseJobs -> {
                log.debug("$username Browsing jobs")
                val jobs = browseJobs()
                log.debug("Browsed ${jobs.size} jobs")
            }

            Action.StartJob -> {
                log.debug("$username wants to start a job")
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
                } else {
                    log.debug("$username already has 2 jobs running. Skipping.")
                }
            }

            Action.StopJob -> {
                log.debug("$username stopping job")

                val job = browseJobs().firstOrNull {
                    it.status.state == JobState.RUNNING && it.owner.createdBy == username
                }

                log.debug("$username Found job: $job.id")

                if (job != null) {
                    Jobs.terminate.call(
                        bulkRequestOf(FindByStringId(job.id)),
                        client
                    ).orThrow()
                }
            }

            Action.OpenInterface -> {
                log.debug("$username open interface")
                val job = browseJobs().firstOrNull {
                    it.owner.createdBy == username &&
                        it.status.state == JobState.RUNNING &&
                        it.status.resolvedApplication?.invocation?.applicationType == ApplicationType.WEB
                }

                log.debug("$username found job: ${job?.id}")
                if (job != null) {
                    Jobs.openInteractiveSession.call(
                        bulkRequestOf(JobsOpenInteractiveSessionRequestItem(job.id, 0, InteractiveSessionType.WEB)),
                        client
                    ).orThrow()
                }
            }

            Action.FollowJob -> {
                log.debug("$username Following job")
                val jobs = browseJobs()
                val running = jobs.firstOrNull { it.status.state == JobState.RUNNING }

                if (running != null) {
                    log.debug("$username Following job ${running.id}")
                    coroutineScope {
                        val job = launch {
                            try {
                                Jobs.follow.subscribe(
                                    FindByStringId(running.id),
                                    wsClient,
                                    handler = { message ->

                                    }
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
                log.debug("$username Done following")
            }

            Action.OpenTerminal -> {
                log.debug("$username open terminal")
                val job = browseJobs().firstOrNull {
                    it.owner.createdBy == username && it.status.state == JobState.RUNNING
                }

                log.debug("$username found job: ${job?.id}")
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
