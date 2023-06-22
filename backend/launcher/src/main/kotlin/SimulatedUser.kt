package dk.sdu.cloud

import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.projects.*
import dk.sdu.cloud.accounting.services.wallets.AccountingProcessor
import dk.sdu.cloud.accounting.services.wallets.AccountingRequest
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.NameAndVersion
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
import kotlin.random.Random
import kotlin.system.exitProcess

class Simulator(
    private val serviceClient: AuthenticatedClient,
    private val numberOfUsers: Int,
) {
    private val users = ArrayList<SimulatedUser>()
    private val projects = ArrayList<Projects>()

    private suspend fun initializeProject(
        title: String,
        pi: SimulatedUser
    ): Project {
        val existingProjects = Projects.browse.call(
            ProjectsBrowseRequest(itemsPerPage = 250),
            serviceClient
        ).orThrow().items

        val existing = existingProjects.find { it.specification.title == title }

        if (existing != null) {
            log.debug("Found existing project")
            return existing
        } else {
            log.debug("Find parent project")
            log.debug("${existingProjects.map { it.specification.title }}")
            val parent = existingProjects.find { it.specification.title == "Provider k8" }!!

            log.debug("Creating new project")

            val projectId = Projects.create.call(
                bulkRequestOf(
                    Project.Specification(
                        parent = parent.id,
                        title = title
                    )
                ),
                serviceClient
            ).orThrow().responses.single().id

            log.debug("Creating invite")

            Projects.createInvite.call(
                bulkRequestOf(
                    ProjectsCreateInviteRequestItem(pi.username)
                ),
                serviceClient.withProject(projectId)
            ).orThrow()


            log.debug("Accept invite")
            pi.acceptInvite(projectId)

            Projects.changeRole.call(
                bulkRequestOf(ProjectsChangeRoleRequestItem(pi.username, ProjectRole.PI)),
                serviceClient.withProject(projectId)
            )

            log.debug("retrieving wallet allocations")
            val allocations = Wallets.retrieveWalletsInternal.call(
                WalletsInternalRetrieveRequest(WalletOwner.Project(parent.id)),
                serviceClient
            ).orThrow().wallets.flatMap { it.allocations }

            log.debug("$allocations")
            log.debug("making deposits")
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

            val project = Projects.browse.call(
                ProjectsBrowseRequest(itemsPerPage = 250),
                users[0].client
            ).orThrow().items.find { it.specification.title == title}!!

            log.debug("project $project")

            return project
        }
    }

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

            log.debug("Writing users to file")
            for (user in newUsers) {
                userFile.appendText("${user.username} ${user.password} ${user.refreshToken}\n")
            }

            users.addAll(newUsers)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        log.debug("Started load simulation")
        val projectTitle = "SimulatedProject3"

        runBlocking {
            initializeUsers()
            project = initializeProject(projectTitle, users[0])
            log.debug("Initialized project")
        }


        // TODO Approve grant applications


        // TODO Start simulation



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

        val simulatedApplication = NameAndVersion("simulated", "1.0.0")

        override val log = logger()
    }
}

private fun ProductCategoryId.toReference(): ProductReference {
    return ProductReference(name, name, provider)
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

    private var projectId: String? = null

    fun initialize(username: String? = null, password: String? = null, refreshToken: String? = null) {
        this.refreshToken = refreshToken
            ?: if (username.isNullOrBlank()) {
                this.username = "sim-${Time.now()}"
                this.password = generatePassword()
                log.debug("Creating user ${this.username}")

                runBlocking {
                    UserDescriptions.createNewUser.call(
                        listOf(
                            CreateSingleUserRequest(
                                username!!,
                                password,
                                "email-${username}@localhost",
                                Role.USER,
                                "First",
                                "Last"
                            )
                        ),
                        serviceClient
                    ).orThrow().single().refreshToken
                }
            } else {
                log.debug("Trying to log in as ${this.username}")

                runBlocking {
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
    }

    suspend fun createProject() {

    }

    suspend fun joinProject(project: String) {
        Projects.createInvite.call(
            bulkRequestOf(ProjectsCreateInviteRequestItem(username)),
            serviceClient.withProject(project)
        ).orThrow()

        Projects.acceptInvite.call(
            bulkRequestOf(FindByProjectId(project)),
            client.withProject(project)
        ).orThrow()

        projectId = project
        client = client.withProject(projectId!!)
    }

    /*suspend fun doStep() {
        var diceRoll = Random.nextInt(100)
        fun chance(chance: Int): Boolean {
            val success = diceRoll <= chance
            diceRoll -= chance
            return success
        }

        when {
            chance(5) -> {
                if (!hasStorage || !(becomesPi || becomesAdmin)) return

                FileCollections.create.call(
                    bulkRequestOf(
                        FileCollection.Spec(
                            UUID.randomUUID().toString().substringBefore('-'),
                            Simulator.storageByQuota.toReference()
                        )
                    ),
                    client
                ).orThrow()
            }

            chance(10) -> {
                if (!hasLinks) return

                val token = Random.nextBytes(16).encodeBase64()
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
                ).orThrow()
            }

            chance(5) -> {
                if (!hasLicenses) return

                Licenses.create.call(
                    bulkRequestOf(
                        LicenseSpecification(Simulator.licenseByQuota.toReference())
                    ),
                    client
                ).orThrow()
            }

            chance(50) -> {
                Jobs.create.call(
                    bulkRequestOf(
                        JobSpecification(
                            Simulator.simulatedApplication,
                            if (hasComputeByHours) Simulator.computeByHours.toReference()
                            else Simulator.computeByCredits.toReference(),
                            parameters = emptyMap(),
                            resources = emptyList(),
                        ),
                    ),
                    client
                ).orThrow()
            }

            else -> {
                // Do nothing
            }
        }
    }*/

    companion object : Loggable {
        override val log = logger()
    }

    private fun generatePassword(): String {
        val pool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..30).map { Random.nextInt(0, pool.size).let { pool[it] }}.joinToString("")
    }
}
