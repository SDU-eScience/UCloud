package dk.sdu.cloud.accounting

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.rpc.*
import dk.sdu.cloud.accounting.services.grants.*
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.accounting.services.projects.FavoriteProjectService
import dk.sdu.cloud.accounting.services.projects.ProjectGroupService
import dk.sdu.cloud.accounting.services.projects.ProjectQueryService
import dk.sdu.cloud.accounting.services.projects.ProjectService
import dk.sdu.cloud.accounting.services.providers.ProviderIntegrationService
import dk.sdu.cloud.accounting.services.providers.ProviderService
import dk.sdu.cloud.accounting.services.serviceJobs.AccountingChecks
import dk.sdu.cloud.accounting.services.serviceJobs.LowFundsJob
import dk.sdu.cloud.accounting.services.wallets.AccountingProcessor
import dk.sdu.cloud.accounting.services.wallets.AccountingService
import dk.sdu.cloud.accounting.services.wallets.DepositNotificationService
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.debug.DebugSystemFeature
import dk.sdu.cloud.grant.rpc.GiftController
import dk.sdu.cloud.grant.rpc.GrantController
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.provider.api.ProviderSupport
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory

class Server(
    override val micro: Micro,
    val config: Configuration
) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val idCardService = IdCardService(db, micro.backgroundScope, client)
        val distributedLocks = DistributedLockFactory(micro)

        val projectCache = ProjectCache(DistributedStateFactory(micro), db)
        val providerProviders =
            dk.sdu.cloud.accounting.util.Providers<ProviderComms>(client) { it }
        val providerSupport = dk.sdu.cloud.accounting.util.ProviderSupport<ProviderComms, Product, ProviderSupport>(
            providerProviders, client, fetchSupport = { emptyList() })
        val providerService = ProviderService(projectCache, db, providerProviders, providerSupport, client)
        val providerIntegrationService = ProviderIntegrationService(
            db, providerService, client,
            micro.developmentModeEnabled
        )

        val simpleProviders = Providers(client) { SimpleProviderCommunication(it.client, it.wsClient, it.provider) }
        val accountingProcessor = AccountingProcessor(
            db,
            micro.feature(DebugSystemFeature).system,
            simpleProviders,
            distributedLocks,
            distributedState = DistributedStateFactory(micro),
            addressToSelf = micro.serviceInstance.ipAddress ?: "127.0.0.1",
            disableMasterElection = micro.commandLineArguments.contains("--single-instance")
        )
        val accountingService = AccountingService(micro.developmentModeEnabled, db, simpleProviders,
            accountingProcessor)

        val productService = ProductService(db, accountingProcessor)

        val depositNotifications = DepositNotificationService(db)
        accountingProcessor.start()

        val projectsV2 = dk.sdu.cloud.accounting.services.projects.v2.ProjectService(
            db, client, projectCache,
            micro.developmentModeEnabled, micro.backgroundScope
        )
        val projectNotifications = dk.sdu.cloud.accounting.services.projects.v2
            .ProviderNotificationService(projectsV2, db, simpleProviders, micro.backgroundScope, client)
        val projectService = ProjectService(client, projectCache, projectsV2)
        val projectGroups = ProjectGroupService(projectCache, projectsV2)
        val projectQueryService = ProjectQueryService(projectService)
        val favoriteProjects = FavoriteProjectService(projectsV2)
        val grants = GrantsV2Service(db, idCardService, accountingService, simpleProviders, projectNotifications,
            client, config.defaultTemplate)
        val giftService = GiftService(db, accountingService, projectService, grants)


        val scriptManager = micro.feature(ScriptManager)
        scriptManager.register(
            Script(
                ScriptMetadata(
                    "accounting-low-funds",
                    "Accounting: Low Funds",
                    WhenToStart.Daily(0, 0)
                ),
                script = {
                    val jobs = LowFundsJob(db, client, config)
                    jobs.checkWallets()
                }
            )
        )

        scriptManager.register(
            Script(
                ScriptMetadata(
                    "project-invite-link-cleanup",
                    "Projects: Clean-up Invite Links",
                    WhenToStart.Daily(0, 0)
                ),
                script = {
                    projectsV2.cleanUpInviteLinks()
                }
            )
        )

        scriptManager.register(
            Script(
                ScriptMetadata(
                    "jobs-vs-transactions-check",
                    "Accounting: Is charges sane",
                    WhenToStart.Daily(0, 0)
                ),
                script = {
                    val accountingChecks = AccountingChecks(db, client)
                    accountingChecks.checkJobsVsTransactions()
                }
            )
        )

        /*
        scriptManager.register(
            Script(
                ScriptMetadata(
                    "center",
                    "Deic report: Center",
                    WhenToStart.Never
                ),
                script = {
                    val postgresDataService = PostgresDataService(db)
                    val deicReporting = DeicReporting(client, postgresDataService)
                    deicReporting.reportCenters()
                }
            )
        )

        scriptManager.register(
            Script(
                ScriptMetadata(
                    "centerDaily",
                    "Deic report: Center Daily",
                    WhenToStart.Never
                ),
                script = {
                    val postgresDataService = PostgresDataService(db)
                    val deicReporting = DeicReporting(client, postgresDataService)
                    deicReporting.reportCenters()
                }
            )
        )

        scriptManager.register(
            Script(
                ScriptMetadata(
                    "Person report",
                    "Deic report: Person",
                    WhenToStart.Never
                ),
                script = {
                    val postgresDataService = PostgresDataService(db)
                    val deicReporting = DeicReporting(client, postgresDataService)
                    deicReporting.reportPerson()
                }
            )
        )
         */

        with(micro.server) {
            configureControllers(
                AccountingController(micro, accountingService, depositNotifications, client),
                ProductController(productService, accountingService, client),
                FavoritesController(db, favoriteProjects),
                GiftController(giftService),
                GrantController(grants),
                GroupController(db, projectGroups, projectQueryService),
                IntegrationController(providerIntegrationService),
                MembershipController(db, projectQueryService),
                ProjectController(db, projectService, projectQueryService),
                ProviderController(
                    providerService,
                    micro.developmentModeEnabled || micro.commandLineArguments.contains("--allow-provider-approval")
                ),
                ProjectsControllerV2(projectsV2, projectNotifications),
            )
        }

        startServices()
    }
}
