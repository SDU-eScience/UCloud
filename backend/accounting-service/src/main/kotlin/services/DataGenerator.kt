package dk.sdu.cloud.accounting.services

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.services.accounting.AccountingPersistence
import dk.sdu.cloud.accounting.services.accounting.AccountingRequest
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.grants.GrantsV2Service
import dk.sdu.cloud.accounting.util.IdCard
import dk.sdu.cloud.accounting.util.IdCardService
import dk.sdu.cloud.auth.api.CreateSingleUserRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.grant.api.*
import dk.sdu.cloud.project.api.ListProjectsRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.service.StaticTimeProvider
import dk.sdu.cloud.service.SystemTimeProvider
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import kotlin.system.exitProcess

class DataGenerator(
    private val system: AccountingSystem,
    private val db: DBContext,
    private val authenticatedClient: AuthenticatedClient,
    private val grantsV2Service: GrantsV2Service,
    private val idCardService: IdCardService,
    private val persistence: AccountingPersistence
) {
    private val testPi = "testPI"
    private val testUser = "testUser"

    suspend fun createTestStructureAndData(actorAndProject: ActorAndProject, request: AccountingV2.AdminGenerateTestData.Request) {
        Time.provider = StaticTimeProvider
        // 1st January 2025
        StaticTimeProvider.time = 1735689600000

        val provider = Projects.listProjects.call(
            ListProjectsRequest(user = actorAndProject.actor.username),
            authenticatedClient
        ).orThrow()

        val p= provider.items.firstOrNull { it.title == "Provider k8" } ?: throw RPCException.fromStatusCode(
            HttpStatusCode.InternalServerError,
            "k8 provider is not initialized"
        )

        if (request.createProjectStructure) {
            createUsers(testPi, testUser)
            createProjects(p.projectId, actorAndProject)
        } else {
            piUser = Actor.User(SecurityPrincipal(testPi, Role.ADMIN, "test", "pi", "test@test.dk", false, serviceAgreementAccepted = true))
            userUser = Actor.User(SecurityPrincipal(testUser, Role.ADMIN, "test", "user", "test@test.dk", false, serviceAgreementAccepted = true))
        }

        if (request.clearExistingUsage) {
            db.withSession { session ->
                session.sendPreparedStatement(
                    //language=sql
                    """
                        delete
                        from accounting.wallet_samples_v2
                        where true;
                    """.trimIndent()
                )
                session.sendPreparedStatement(
                    //language=sql
                    """
                        update accounting.wallet_allocations_v2
                        set retired_usage = 0
                        where true;
                    """.trimIndent()
                )
                session.sendPreparedStatement(
                    //language=sql
                    """
                        update accounting.allocation_groups
                        set tree_usage = 0, retired_tree_usage = 0
                        where true;
                    """.trimIndent()
                )
                session.sendPreparedStatement(
                    //language=sql
                    """
                        update accounting.wallets_v2
                        set local_usage = 0, local_retired_usage = 0, excess_usage = 0, was_locked = false
                        where true;
                    """.trimIndent()
                )
            }
            println("Need to restart to take effect - exiting")
            exitProcess(0)
        }

        if (request.makeCharges) {
            createUsageData(request)
        }
        Time.provider = SystemTimeProvider
    }

    private lateinit var piUser: Actor.User
    private lateinit var userUser: Actor.User

    private suspend fun createUsers(piName: String, testUserName: String) {
        UserDescriptions.createNewUser.call(
            listOf(
                CreateSingleUserRequest(
                    username = piName,
                    password = "mypassword",
                    email = "test@test.dk",
                    role = Role.ADMIN,
                    firstnames = "test",
                    lastname = "pi",
                    orgId = null
                ),
                CreateSingleUserRequest(
                    username = testUserName,
                    password = "mypassword",
                    email = "test@test.dk",
                    role = Role.USER,
                    firstnames = "test",
                    lastname = "user",
                    orgId = null
                )
            ),
            authenticatedClient
        ).orThrow()
        piUser = Actor.User(SecurityPrincipal(piName, Role.ADMIN, "test", "pi", "test@test.dk", false, serviceAgreementAccepted = true))
        userUser = Actor.User(SecurityPrincipal(testUserName, Role.ADMIN, "test", "user", "test@test.dk", false, serviceAgreementAccepted = true))
    }

    private val MINUTE = 1000L * 60
    private val HOUR = MINUTE * 60
    private val DAY = HOUR * 24
    private val YEAR = DAY * 365

    // Generates project structure with credits
    private suspend fun createProjects(rootProjectId: String, actorAndProject: ActorAndProject) {
        var grantId = grantsV2Service.submitRevision(
            ActorAndProject(piUser, null),
            GrantsV2.SubmitRevision.Request(
                GrantApplication.Document(
                    GrantApplication.Recipient.NewProject("sub1"),
                    listOf(
                        GrantApplication.AllocationRequest(
                            "cpu-h",
                            "k8",
                            rootProjectId,
                            500_000,
                            GrantApplication.Period(
                                Time.now(),
                                Time.now() + YEAR
                            ),
                            "Provider k8"
                        ),
                        GrantApplication.AllocationRequest(
                            "storage",
                            "k8",
                            rootProjectId,
                            500_000,
                            GrantApplication.Period(
                                Time.now(),
                                Time.now() + YEAR
                            ),
                            "Provider k8"
                        )
                    ),
                    GrantApplication.Form.PlainText("mojn"),
                    emptyList(),
                    parentProjectId = rootProjectId
                ),
                "grant application"
            )
        ).id

        grantsV2Service.updateState(
            ActorAndProject(actorAndProject.actor, rootProjectId),
            GrantsV2.UpdateState.Request(grantId, GrantApplication.State.APPROVED)
        )

        grantId = grantsV2Service.submitRevision(
            ActorAndProject(piUser, null),
            GrantsV2.SubmitRevision.Request(
                GrantApplication.Document(
                    GrantApplication.Recipient.NewProject("sub2"),
                    listOf(
                        GrantApplication.AllocationRequest(
                            "cpu-h",
                            "k8",
                            rootProjectId,
                            500_000,
                            GrantApplication.Period(
                                Time.now(),
                                Time.now() + YEAR
                            ),
                            "Provider k8"
                        ),
                        GrantApplication.AllocationRequest(
                            "storage",
                            "k8",
                            rootProjectId,
                            500_000,
                            GrantApplication.Period(
                                Time.now(),
                                Time.now() + YEAR
                            ),
                            "Provider k8"
                        )
                    ),
                    GrantApplication.Form.PlainText("mojn"),
                    emptyList(),
                    parentProjectId = rootProjectId
                ),
                "grant application"
            )
        ).id

        grantsV2Service.updateState(
            ActorAndProject(actorAndProject.actor, rootProjectId),
            GrantsV2.UpdateState.Request(grantId, GrantApplication.State.APPROVED)
        )

        StaticTimeProvider.time += DAY

        val projects = Projects.listProjects.call(
            ListProjectsRequest(
                piUser.username
            ),
            authenticatedClient
        ).orThrow().items

        for (project in projects) {
            when (project.title) {
                "sub1" -> {
                    println("Handling subs for sub1")
                    grantsV2Service.submitRevision(
                        ActorAndProject(piUser, project.projectId),
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                GrantApplication.Recipient.NewProject("sub11"),
                                listOf(
                                    GrantApplication.AllocationRequest(
                                        "cpu-h",
                                        "k8",
                                        project.projectId,
                                        5_000,
                                        GrantApplication.Period(
                                            Time.now(),
                                            Time.now() + YEAR
                                        ),
                                        project.title
                                    ),
                                    GrantApplication.AllocationRequest(
                                        "storage",
                                        "k8",
                                        project.projectId,
                                        50_000,
                                        GrantApplication.Period(
                                            Time.now(),
                                            Time.now() + YEAR
                                        ),
                                        project.title
                                    )
                                ),
                                GrantApplication.Form.GrantGiverInitiated("mojn"),
                                emptyList(),
                                parentProjectId = project.projectId
                            ),
                            "grant application"
                        )
                    ).id
                }
                "sub2" -> {
                    println("Handling subs for sub2")
                    println("sub21")
                    grantsV2Service.submitRevision(
                        ActorAndProject(piUser, project.projectId),
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                GrantApplication.Recipient.NewProject("sub21"),
                                listOf(
                                    GrantApplication.AllocationRequest(
                                        "cpu-h",
                                        "k8",
                                        project.projectId,
                                        5_000,
                                        GrantApplication.Period(
                                            Time.now(),
                                            Time.now() + YEAR
                                        ),
                                        project.title
                                    ),
                                    GrantApplication.AllocationRequest(
                                        "storage",
                                        "k8",
                                        project.projectId,
                                        50_000,
                                        GrantApplication.Period(
                                            Time.now(),
                                            Time.now() + YEAR
                                        ),
                                        project.title
                                    )
                                ),
                                GrantApplication.Form.GrantGiverInitiated("mojn"),
                                emptyList(),
                                parentProjectId = project.projectId
                            ),
                            "grant application"
                        )
                    ).id

                    println("sub22")

                    grantsV2Service.submitRevision(
                        ActorAndProject(piUser, project.projectId),
                        GrantsV2.SubmitRevision.Request(
                            GrantApplication.Document(
                                GrantApplication.Recipient.NewProject("sub22"),
                                listOf(
                                    GrantApplication.AllocationRequest(
                                        "cpu-h",
                                        "k8",
                                        project.projectId,
                                        5_000,
                                        GrantApplication.Period(
                                            Time.now(),
                                            Time.now() + YEAR
                                        ),
                                        project.title
                                    ),
                                    GrantApplication.AllocationRequest(
                                        "storage",
                                        "k8",
                                        project.projectId,
                                        50_000,
                                        GrantApplication.Period(
                                            Time.now(),
                                            Time.now() + YEAR
                                        ),
                                        project.title
                                    )
                                ),
                                GrantApplication.Form.GrantGiverInitiated("mojn"),
                                emptyList(),
                                parentProjectId = project.projectId
                            ),
                            "grant application"
                        )
                    ).id
                }
                else -> {
                    println("Skipping ${project.title}")
                }
            }

        }

        StaticTimeProvider.time += DAY

        println("adding more funds to sub21")

        val subID = Projects.listProjects.call(
            ListProjectsRequest(
                piUser.username
            ),
            authenticatedClient
        ).orThrow().items.firstOrNull( { it.title == "sub21" } )

        if (subID != null) {

            grantsV2Service.submitRevision(
                ActorAndProject(piUser, subID.projectId),
                GrantsV2.SubmitRevision.Request(
                    GrantApplication.Document(
                        GrantApplication.Recipient.ExistingProject(subID.projectId),
                        listOf(
                            GrantApplication.AllocationRequest(
                                "cpu-h",
                                "k8",
                                rootProjectId,
                                5_000,
                                GrantApplication.Period(
                                    Time.now(),
                                    Time.now() + YEAR
                                ),
                                "Provider k8"
                            ),
                            GrantApplication.AllocationRequest(
                                "storage",
                                "k8",
                                rootProjectId,
                                50_000,
                                GrantApplication.Period(
                                    Time.now(),
                                    Time.now() + YEAR
                                ),
                                "Provider k8"
                            )
                        ),
                        GrantApplication.Form.PlainText("mojn"),
                        emptyList(),
                        parentProjectId = rootProjectId
                    ),
                    "grant application"
                )
            ).id
        }
    }

    private suspend fun createUsageData(request: AccountingV2.AdminGenerateTestData.Request) {
        val chargeableProjects = Projects.listProjects.call(
            ListProjectsRequest(
                piUser.username,
            ),
            authenticatedClient
        ).orThrow().items.map { it.projectId }
        //Deletes samples to remove wierd usage graphs
        db.withSession { session ->
            session.sendPreparedStatement(
                //language=sql
                """
                delete
                from accounting.wallet_samples_v2
                where true;
            """.trimIndent()
            )
        }
        var timeLeft = request.timeSpanInMillis
        var cpuleft = request.coreHourUsage
        var maxStorage = request.maxStorageUsage
        val chargeableCPUProjects = chargeableProjects.toMutableList()

        persistence.setLastSampling(Time.now())
        persistence.setNextSynch(Time.now() - 100)

        val cpu = ProductCategoryIdV2(
            "cpu-h",
            "k8"
        )


        val storage = ProductCategoryIdV2(
            "storage",
            "k8"
        )

        val differentStorageCharges = mutableListOf<Pair<String, Long>>()

        //Start the next day
        StaticTimeProvider.time = Time.now() + 1000 * 60 * 60 * 24

        while (timeLeft > 0 || cpuleft > 0) {
            val randomProjectCPU = chargeableCPUProjects.random()
            val chargeAmount = (1..500L).random()

            try {
                system.sendRequest(
                    AccountingRequest.Charge(
                        idCard = IdCard.System,
                        owner = randomProjectCPU,
                        category = cpu,
                        amount = chargeAmount,
                        isDelta = true,
                    )
                )
            } catch (e: RPCException) {
                if (e.httpStatusCode == HttpStatusCode.PaymentRequired) {
                    chargeableCPUProjects.remove(randomProjectCPU)
                }
            }

            //25 percent change of making a storage charge
            if ((1..100).random() <= 25) {
                val storageUsed = (0..maxStorage).random()
                val randomProjectStorage = chargeableProjects.random()
                system.sendRequest(
                    AccountingRequest.Charge(
                        idCard = IdCard.System,
                        owner = randomProjectStorage,
                        category = storage,
                        amount = storageUsed,
                        isDelta = false,
                    )
                )
                differentStorageCharges.add(Pair(randomProjectStorage, storageUsed))
            }


            //Increase with 2 minutes
            val timespan = 1000L * 60 * 60 * 1
            StaticTimeProvider.time = Time.now() + timespan
            timeLeft - timespan
            cpuleft -= chargeAmount
            persistence.flushChanges()
            persistence.setNextSynch(Time.now() - 100)
            persistence.setLastSampling(Time.now() - 1000L * 60 * 60 * 24)

            if (timeLeft <= 0) {
                println("Time spend. Total charged values was - cpu: ${request.coreHourUsage - cpuleft}, storage: $differentStorageCharges")
                break
            }

            if (cpuleft <= 0) {
                println("All CPU spend. Total charged values was - cpu: ${request.coreHourUsage}, storage: $differentStorageCharges")
                break
            }
        }
    }
}