package dk.sdu.cloud

import dk.sdu.cloud.accounting.AccountingService
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.projects.*
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
    private val numberOfUsers: Int
) {
    val simulationId = Random.nextBytes(16).encodeBase64()
        .replace("+", "")
        .replace("-", "")
        .replace("=", "")
        .replace("/", "")

    private val users = ArrayList<SimulatedUser>()
    private lateinit var project: Project

    //fun allocatePi(): SimulatedUser = users.filter { it.becomesPi }.random()
    suspend fun requestGrantApproval(id: Long) {
        Grants.updateApplicationState.call(
            bulkRequestOf(
                UpdateApplicationState(
                    id,
                    GrantApplication.State.APPROVED,
                    false
                )
            ),
            serviceClient
        ).orThrow()
    }

    private fun applyForResources() {
        runBlocking {
            Grants.submitApplication.call(
                bulkRequestOf(
                    users.map { user ->
                        CreateApplication(
                            GrantApplication.Document(
                                GrantApplication.Recipient.PersonalWorkspace(user.username),
                                listOf(
                                    GrantApplication.AllocationRequest(
                                        computeByHours.name,
                                        computeByHours.provider,
                                        "k8",
                                        100_000L,
                                        period = GrantApplication.Period(System.currentTimeMillis() - 1000L, null)
                                    ),
                                    GrantApplication.AllocationRequest(
                                        storageByQuota.name,
                                        storageByQuota.provider,
                                        "k8",
                                        100_000L,
                                        period = GrantApplication.Period(System.currentTimeMillis() - 1000L, null)
                                    )
                                ),
                                GrantApplication.Form.PlainText("Grant application request from simulated user")
                            )
                        )
                    }
                ),
                serviceClient
            )
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
            val (username, password) = existingUser.split(" ")
            val simUser = SimulatedUser(serviceClient)
            simUser.initialize(username, password)
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
                userFile.appendText("${user.username} ${user.password}\n")
            }

            users.addAll(newUsers)
        }
    }

    /*fun initializeProject(): Project {
        project = Projects.browse.call(
            ProjectsBrowseRequest(itemsPerPage = 250),
            serviceClient
        ).orThrow().items.single().id

        Projects.browse.call(
            ProjectsBrowseRequest()
        )
    }*/

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        log.debug("Started load simulation")

        runBlocking {
            initializeUsers()
        }

        //project = initializeProject()


        // TODO Apply for resources



        // TODO Approve grant applications


        // TODO Start simulation



        return
            /*val root = Projects.create.call(
                bulkRequestOf(
                    Project.Specification(null, "Simulation-Root-${simulationId}"),
                ),
                serviceClient
            ).orThrow().responses.single().id

            GrantsEnabled.setEnabledStatus.call(
                bulkRequestOf(SetEnabledStatusRequest(root, true)),
                serviceClient
            ).orThrow()

            GrantSettings.uploadRequestSettings.call(
                bulkRequestOf(
                    ProjectApplicationSettings(
                        root,
                        listOf(UserCriteria.Anyone()),
                        emptyList()
                    )
                ),
                serviceClient.withProject(root)
            ).orThrow()*/

            /*Accounting.rootDeposit.call(
                bulkRequestOf(
                    RootDepositRequestItem(computeByHours, WalletOwner.Project(root), 1_000_000L * 1_000_000_000, "Root"),
                    RootDepositRequestItem(computeByCredits, WalletOwner.Project(root), 1_000_000L * 1_000_000_000, "Root"),
                    RootDepositRequestItem(licenseByQuota, WalletOwner.Project(root), 1_000_000L * 1_000_000_000, "Root"),
                    RootDepositRequestItem(storageByQuota, WalletOwner.Project(root), 1_000_000L * 1_000_000_000, "Root"),
                ),
                serviceClient
            ).orThrow()

            // Force the first user to be a PI (that way allocatePi() will always succeed)
            users.add(SimulatedUser(serviceClient, root, this@Simulator, forcePi = true).also { it.initialStep() })

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
        val computeByHours = ProductCategoryId("cpu-1", "k8")
        val storageByQuota = ProductCategoryId("storage", "k8")

        val simulatedApplication = NameAndVersion("simulated", "1.0.0")

        override val log = logger()
    }
}

private fun ProductCategoryId.toReference(): ProductReference {
    return ProductReference(name, name, provider)
}

class SimulatedUser(
    private val serviceClient: AuthenticatedClient
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
    lateinit var client: AuthenticatedClient
    lateinit var projectId: String

    suspend fun initialize(username: String? = null, password: String? = null) {
        val refreshToken: String = if (username.isNullOrBlank()) {
            this.username = "sim-${Time.now()}"
            this.password = generatePassword()
            log.debug("Creating user ${this.username} with password ${this.password}")
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
            this.username = username
            this.password = password ?: ""
            log.debug("Trying to log in as ${this.username}")

            val testCall = News.listPosts.call(ListPostsRequest(itemsPerPage = 10, page = 0, withHidden = false), serviceClient).ctx as OutgoingHttpCall
            log.debug("${testCall.response}")
            val call = AuthDescriptions.passwordLogin.call(
                Unit,
                serviceClient.withHttpBody(
                    """
                        -----boundary
                        Content-Disposition: form-data; name="service"

                        web
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

            val response = call.response!!
            log.debug("${call.attributes}")
            log.debug("body: ${response.bodyAsText()}")
            val cookiesSet = response.headers[HttpHeaders.SetCookie]!!
            val split = cookiesSet.split(";").associate {
                val key = it.substringBefore('=')
                val value = URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                key to value
            }

            log.debug("$split")

            split["refreshToken"]!!
        }

        log.debug("${this.username} logged in with token: $refreshToken")
        client = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Normal(refreshToken, OutgoingHttpCall)
        ).authenticateClient(OutgoingHttpCall)

        /*if (becomesPi) {
            val grantId = Grants.submitApplication.call(
                bulkRequestOf(
                    CreateApplication(
                        GrantApplication.Document(
                            GrantApplication.Recipient.NewProject("${simulator.simulationId} ${projectIdGenerator.getAndIncrement()}"),
                            buildList {
                                fun request(categoryId: ProductCategoryId): GrantApplication.AllocationRequest =
                                    GrantApplication.AllocationRequest(categoryId.name, categoryId.provider, parentProject, 50_000 * 1_000_000L, period = GrantApplication.Period(System.currentTimeMillis()-1000L, null))
                                //if (hasComputeByCredits) add(request(Simulator.computeByCredits))
                                //if (hasComputeByHours) add(request(Simulator.computeByHours))
                                //if (hasLicenses) add(request(Simulator.licenseByQuota))
                                //if (hasStorage) add(request(Simulator.storageByQuota))
                            },
                            GrantApplication.Form.PlainText("This is my application"),
                        )
                    )
                ),
                client
            ).orThrow().responses.first().id

            //simulator.requestGrantApproval(grantId)

            projectId = Projects.browse.call(
                ProjectsBrowseRequest(itemsPerPage = 250),
                client
            ).orThrow().items.single().id

            client = client.withProject(projectId)
        } else {
            //simulator.allocatePi().requestInvite(this)
        }*/
    }

    /*suspend fun requestInvite(member: SimulatedUser) {
        Projects.createInvite.call(
            bulkRequestOf(ProjectsCreateInviteRequestItem(member.username)),
            client
        ).orThrow()

        Projects.acceptInvite.call(
            bulkRequestOf(FindByProjectId(projectId)),
            member.client
        ).orThrow()

        if (member.becomesAdmin) {
            Projects.changeRole.call(
                bulkRequestOf(ProjectsChangeRoleRequestItem(member.username, ProjectRole.ADMIN)),
                client
            ).orThrow()
        }

        member.projectId = projectId
        member.client = member.client.withProject(projectId)
        /*member.hasLicenses = hasLicenses
        member.hasLinks = hasLinks
        member.hasStorage = hasStorage
        member.hasComputeByCredits = hasComputeByCredits
        member.hasComputeByHours = hasComputeByHours*/
    }*/

    suspend fun doStep() {
        var diceRoll = Random.nextInt(100)
        fun chance(chance: Int): Boolean {
            val success = diceRoll <= chance
            diceRoll -= chance
            return success
        }

        /*when {
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
        }*/
    }

    companion object : Loggable {
        override val log = logger()
    }

    private fun generatePassword(): String {
        val pool = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..30).map { Random.nextInt(0, pool.size).let { pool[it] }}.joinToString("")
    }
}
