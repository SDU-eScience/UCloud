package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.projects.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppStore
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.ToolStore
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollections
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.project.api.v2.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.random.Random
import kotlin.system.exitProcess

class Simulator(
    private val serviceClient: AuthenticatedClient,
    private val numberOfUsers: Int,
    private val numberOfSteps: Int,
) {
    val simulationId = Random.nextBytes(16).encodeBase64()
        .replace("+", "")
        .replace("-", "")
        .replace("=", "")
        .replace("/", "")

    private val users = ArrayList<SimulatedUser>()

    fun allocatePi(): SimulatedUser = users.filter { it.becomesPi }.random()
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

    private suspend fun createProduct(product: Product) {
        val hasProduct = Products.retrieve.call(
            ProductsRetrieveRequest(
                filterName = product.name,
                filterCategory = product.category.name,
                filterProvider = product.category.provider
            ),
            serviceClient
        ).orNull() != null

        if (hasProduct) return
        Products.create.call(bulkRequestOf(product), serviceClient)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        GlobalScope.launch {
            createProduct(
                Product.Storage(
                    storageByQuota.name,
                    1L,
                    storageByQuota,
                    "Storage by quota",
                    unitOfPrice = ProductPriceUnit.PER_UNIT,
                    chargeType = ChargeType.DIFFERENTIAL_QUOTA
                )
            )

            createProduct(
                Product.Compute(
                    computeByHours.name,
                    1L,
                    computeByHours,
                    "Compute by hours",
                    cpu = 1,
                    memoryInGigs = 1,
                    gpu = 0,
                    unitOfPrice = ProductPriceUnit.UNITS_PER_MINUTE,
                    chargeType = ChargeType.ABSOLUTE
                )
            )

            createProduct(
                Product.Compute(
                    computeByCredits.name,
                    1L,
                    computeByCredits,
                    "Compute by credits",
                    cpu = 1,
                    memoryInGigs = 1,
                    gpu = 0,
                    unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE,
                    chargeType = ChargeType.ABSOLUTE
                )
            )

            createProduct(
                Product.Ingress(
                    linkFree.name,
                    1L,
                    linkFree,
                    "Public link product",
                    freeToUse = true,
                )
            )

            ToolStore.create.call(
                Unit,
                serviceClient.withHttpBody(
                    """
                        ---
                        tool: v1

                        title: Simulated Application
                        name: ${simulatedApplication.name}
                        version: ${simulatedApplication.version}

                        container: alpine:3

                        authors:
                        - Dan
                          
                        defaultTimeAllocation:
                          hours: 1
                          minutes: 0
                          seconds: 0

                        description: All
                                   
                        defaultNumberOfNodes: 1 
                        defaultTasksPerNode: 1

                        backend: DOCKER
                    """.trimIndent()
                )
            )

            AppStore.create.call(
                Unit,
                serviceClient.withHttpBody(
                    """
                        application: v1

                        title: Simulated Application
                        name: ${simulatedApplication.name}
                        version: ${simulatedApplication.version}

                        applicationType: BATCH

                        tool:
                          name: alpine
                          version: 1

                        authors:
                        - Dan

                        container:
                          runAsRoot: true
                         
                        description: An application used for user simulations. This application never runs and registers a random amount of consumption.
                         
                        invocation:
                        - exit
                        - 0

                        outputFileGlobs:
                          - "*"

                    """.trimIndent()
                )
            )

            val root = Projects.create.call(
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
                        AutomaticApprovalSettings(emptyList(), emptyList()),
                        listOf(UserCriteria.Anyone()),
                        emptyList()
                    )
                ),
                serviceClient.withProject(root)
            ).orThrow()

            Accounting.rootDeposit.call(
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
                    repeat(numberOfSteps) {
                        chunk.forEach { it.doStep() }
                        counter[index] = counter[index] + chunk.size
                    }
                    println("Done!")
                }
            }.joinAll()
            statJob.cancel()

            println("Completed $numberOfSteps")
        }
    }

    companion object {
        val computeByCredits = ProductCategoryId("cpu-dkk", "ucloud")
        val computeByHours = ProductCategoryId("cpu-core", "ucloud")
        val storageByQuota = ProductCategoryId("u1-cephfs", "ucloud")
        val licenseByQuota = ProductCategoryId("license-quota", "ucloud")
        val linkFree = ProductCategoryId("u1-publiclink", "ucloud")

        val simulatedApplication = NameAndVersion("simulated", "1.0.0")
    }
}

private fun ProductCategoryId.toReference(): ProductReference {
    return ProductReference(name, name, provider)
}

class SimulatedUser(
    private val serviceClient: AuthenticatedClient,
    private val parentProject: String,
    private val simulator: Simulator,
    forcePi: Boolean = false,
) {
    val becomesPi = forcePi || Random.nextInt(100) <= 10
    val becomesAdmin = !becomesPi && Random.nextInt(100) <= 30
    val becomesUser = !becomesPi && !becomesAdmin

    var hasComputeByCredits = Random.nextBoolean()
    var hasComputeByHours = !hasComputeByCredits
    var hasLicenses = Random.nextBoolean()
    var hasStorage = true
    var hasLinks = Random.nextBoolean()

    lateinit var username: String
    lateinit var client: AuthenticatedClient
    lateinit var projectId: String

    suspend fun initialStep() {
        username = when {
            becomesPi -> "pi-${simulator.simulationId}-${userIdGenerator.getAndIncrement()}"
            becomesAdmin -> "admin-${simulator.simulationId}-${userIdGenerator.getAndIncrement()}"
            else -> "user-${simulator.simulationId}-${userIdGenerator.getAndIncrement()}"
        }

        val refreshToken = UserDescriptions.createNewUser.call(
            listOf(
                CreateSingleUserRequest(
                    username,
                    "simulated-user",
                    "email-${username}@localhost",
                    Role.USER,
                    "First",
                    "Last"
                )
            ),
            serviceClient
        ).orThrow().single().refreshToken

        client = RefreshingJWTAuthenticator(
            serviceClient.client,
            JwtRefresher.Normal(refreshToken, OutgoingHttpCall)
        ).authenticateClient(OutgoingHttpCall)

        if (becomesPi) {
            val grantId = Grants.submitApplication.call(
                bulkRequestOf(
                    CreateApplication(
                        GrantApplication.Document(
                            GrantApplication.Recipient.NewProject("${simulator.simulationId} ${projectIdGenerator.getAndIncrement()}"),
                            buildList {
                                fun request(categoryId: ProductCategoryId): GrantApplication.AllocationRequest =
                                    GrantApplication.AllocationRequest(categoryId.name, categoryId.provider, parentProject, 50_000 * 1_000_000L, period = GrantApplication.Period(System.currentTimeMillis()-1000L, null))
                                if (hasComputeByCredits) add(request(Simulator.computeByCredits))
                                if (hasComputeByHours) add(request(Simulator.computeByHours))
                                if (hasLicenses) add(request(Simulator.licenseByQuota))
                                if (hasStorage) add(request(Simulator.storageByQuota))
                            },
                            GrantApplication.Form.PlainText("This is my application"),
                        )
                    )
                ),
                client
            ).orThrow().responses.first().id

            simulator.requestGrantApproval(grantId)

            projectId = Projects.browse.call(
                ProjectsBrowseRequest(itemsPerPage = 250),
                client
            ).orThrow().items.single().id

            client = client.withProject(projectId)
        } else {
            simulator.allocatePi().requestInvite(this)
        }
    }

    suspend fun requestInvite(member: SimulatedUser) {
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
        member.hasLicenses = hasLicenses
        member.hasLinks = hasLinks
        member.hasStorage = hasStorage
        member.hasComputeByCredits = hasComputeByCredits
        member.hasComputeByHours = hasComputeByHours
    }

    suspend fun doStep() {
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
    }

    companion object {
        val userIdGenerator = AtomicInteger(0)
        val projectIdGenerator = AtomicInteger(0)
    }
}
