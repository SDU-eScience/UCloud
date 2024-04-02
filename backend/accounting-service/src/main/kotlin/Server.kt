package dk.sdu.cloud.accounting

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.rpc.*
import dk.sdu.cloud.accounting.services.accounting.AccountingSystem
import dk.sdu.cloud.accounting.services.accounting.DataVisualization
import dk.sdu.cloud.accounting.services.accounting.RealAccountingPersistence
import dk.sdu.cloud.accounting.services.grants.*
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.accounting.services.projects.FavoriteProjectService
import dk.sdu.cloud.accounting.services.projects.ProjectGroupService
import dk.sdu.cloud.accounting.services.projects.ProjectQueryService
import dk.sdu.cloud.accounting.services.projects.ProjectService
import dk.sdu.cloud.accounting.services.providers.ProviderIntegrationService
import dk.sdu.cloud.accounting.services.providers.ProviderService
import dk.sdu.cloud.accounting.services.wallets.DepositNotificationService
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.grant.rpc.GiftController
import dk.sdu.cloud.grant.rpc.GrantController
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.provider.api.ProviderSupport
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import kotlinx.coroutines.launch

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
        val distributedStateFactory = DistributedStateFactory(micro)

        val projectCache = ProjectCache(DistributedStateFactory(micro), db)
        val providerProviders =
            Providers<ProviderComms>(client) { it }
        val providerSupport = ProviderSupport<ProviderComms, Product, ProviderSupport>(
            providerProviders, client, fetchSupport = { emptyList() })
        val providerService = ProviderService(projectCache, db, providerProviders, providerSupport, client)
        val providerIntegrationService = ProviderIntegrationService(
            db, providerService, client,
            micro.developmentModeEnabled
        )

        val simpleProviders = Providers(client) { SimpleProviderCommunication(it.client, it.wsClient, it.provider) }
        val productCache = ProductCache(db)
        val accountingSystem = AccountingSystem(
            productCache,
            RealAccountingPersistence(db),
            IdCardService(db, micro.backgroundScope, client),
            distributedLocks,
            micro.developmentModeEnabled,
            distributedStateFactory,
            addressToSelf = micro.serviceInstance.ipAddress ?: "127.0.0.1",
        )

        val productService = ProductService(db, accountingSystem, idCardService)

        val depositNotifications = DepositNotificationService(db)

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
        val grants = GrantsV2Service(db, idCardService, accountingSystem, simpleProviders, projectNotifications,
            client, config.defaultTemplate)
        val giftService = GiftService(db, accountingSystem, projectService, grants, idCardService)
        val dataVisualization = DataVisualization(db, accountingSystem)

        accountingSystem.start(micro.backgroundScope)
        micro.backgroundScope.launch {
            grants.init()
        }

        val scriptManager = micro.feature(ScriptManager)
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

        configureControllers(
            AccountingController(accountingSystem, dataVisualization, depositNotifications, idCardService, client),
            ProductController(productService, accountingSystem, client),
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

        startServices()
    }
}
