package dk.sdu.cloud

import dk.sdu.cloud.Simulator.Companion.terminalApplication
import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.projects.*
import dk.sdu.cloud.accounting.services.wallets.AccountingProcessor
import dk.sdu.cloud.accounting.services.wallets.AccountingRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.news.api.ListPostsRequest
import dk.sdu.cloud.news.api.News
import dk.sdu.cloud.news.api.NewsServiceDescription
import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.project.api.v2.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.mapItems
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.serializer
import org.jetbrains.kotlin.backend.common.push
import org.slf4j.Logger
import java.io.File
import java.math.BigInteger
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random
import kotlin.system.exitProcess

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

    @OptIn(DelicateCoroutinesApi::class)
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

            // Force the first user to be a PI (that way allocatePi() will always succeed)
            /*users.add(SimulatedUser(serviceClient, root, this@Simulator, forcePi = true).also { it.initialStep() })

            repeat(numberOfUsers - 1) {
                val user = SimulatedUser(serviceClient, root, this@Simulator, forcePi = false)
                users.add(user)
                user.initialStep()
            }

            val chunks = users.chunked(users.size / 8)
            val counter = Array<Long>(chunks.size) { 0 }
            val statJob = launch {
                val lastCounts = Array<Long>(chunks.size) { 0 }
                while (isActive) {
                    for (i in counter.indices) {
                        val newCounter = counter[i]
                        val oldCounter = lastCounts[i]

                        println("[$i] ${(newCounter - oldCounter) / 10.0} steps per second")
                        lastCounts[i] = newCounter
                    }
                    println()

                    delay(10_000)
                }
            }

            chunks.mapIndexed { index, chunk ->
                println("Got a chunk! $chunk")
                launch {
                    println("Steps!")
                    /*repeat(numberOfSteps) {
                        chunk.forEach { it.doStep() }
                        counter[index] = counter[index] + chunk.size
                    }*/
                    println("Done!")
                }
            }.joinAll()
            statJob.cancel()*/

            //println("Completed $numberOfSteps")
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
    //val isPi = forcePi || Random.nextInt(100) <= 10
    //val becomesAdmin = !becomesPi && Random.nextInt(100) <= 30
    //val becomesUser = !becomesPi && !becomesAdmin

    //var hasComputeByCredits = Random.nextBoolean()
    //var hasComputeByHours = !hasComputeByCredits
    //var hasLicenses = Random.nextBoolean()
    //var hasStorage = true
    //var hasLinks = Random.nextBoolean()

    lateinit var username: String
    lateinit var password: String
    lateinit var refreshToken: String
    lateinit var client: AuthenticatedClient
    lateinit var project: Project

    suspend fun initialize(username: String? = null, password: String? = null, refreshToken: String? = null) {
        this.refreshToken = if (refreshToken != null) {
            this.username = username ?: ""
            this.password = password ?: ""
            refreshToken
        } else {
            if (username.isNullOrBlank()) {
                this.username = "sim-${Time.now()}"
                this.password = generatePassword()
                log.debug("Creating user ${this.username}")

                UserDescriptions.createNewUser.call(
                    listOf(
                        CreateSingleUserRequest(
                            this.username,
                            this.password,
                            "email-${username}@localhost",
                            Role.USER,
                            "First",
                            "Last"
                        )
                    ),
                    serviceClient
                ).orThrow().single().refreshToken
            } else {
                log.debug("Trying to log in as ${this.username}")

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

                split["refreshToken"]!!
            }
        }

        log.debug("${this.username} logged in with token: ${this.refreshToken}")
        client = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Normal(this.refreshToken, OutgoingHttpCall)
        ).authenticateClient(OutgoingHttpCall)

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
            doStep()

            val delayMillis = Random.nextLong(1000, 5000)
            delay(delayMillis)
        }
        println("Done $username")
    }

    private suspend fun doStep() {
        println("Hello from $username")
        fun chance(chance: Int): Boolean {
            var diceRoll = Random.nextInt(100)
            log.debug("$username $chance $diceRoll ${diceRoll<=chance}");
            val success = diceRoll <= chance
            return success
        }

        when {
            chance(5) -> {
                /*
                //if (!hasStorage || !(becomesPi || becomesAdmin)) return

                FileCollections.create.call(
                    bulkRequestOf(
                        FileCollection.Spec(
                            UUID.randomUUID().toString().substringBefore('-'),
                            Simulator.storageByQuota.toReference()
                        )
                    ),
                    client
                ).orThrow()*/
                log.debug("$username Did something with 5% chance")
            }

            chance(10) -> {
                log.debug("$username Did something with 10% chance")
                //if (!hasLinks) return

                /*val token = Random.nextBytes(16).encodeBase64()
                    .replace("+", "")
                    .replace("=", "")
                    .replace("-", "")
                    .replace("/", "")

                Ingresses.create.call(
                    bulkRequestOf(
                        IngressSpecification(
                            "app-$token.cloud.sdu.dk",
                            Simulator.linkFree.toReference()
                        )
                    ),
                    client
                ).orThrow()*/
            }

            chance(5) -> {
                log.debug("$username Did something with 5% chance")
                //if (!hasLicenses) return

                /*Licenses.create.call(
                    bulkRequestOf(
                        LicenseSpecification(Simulator.licenseByQuota.toReference())
                    ),
                    client
                ).orThrow()*/
            }



            chance(20) -> {
                log.debug("$username Starting application ")
                Jobs.create.call(
                    bulkRequestOf(
                        JobSpecification(
                            terminalApplication
                            ,
                            Simulator.computeByHours.toReference(),
                            parameters = emptyMap(),
                            resources = emptyList(),
                            timeAllocation = SimpleDuration(1, 0, 0)
                        ),
                    ),
                    client
                ).orThrow()
            }

            else -> {
                // Do nothing
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }

    private fun generatePassword(): String {
        val pool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..30).map { Random.nextInt(0, pool.size).let { pool[it] }}.joinToString("")
    }
}
