package dk.sdu.cloud.accounting

import dk.sdu.cloud.accounting.rpc.*
import dk.sdu.cloud.accounting.services.*
import dk.sdu.cloud.accounting.services.grants.*
import dk.sdu.cloud.accounting.services.products.ProductService
import dk.sdu.cloud.accounting.services.projects.FavoriteProjectService
import dk.sdu.cloud.accounting.services.projects.ProjectGroupService
import dk.sdu.cloud.accounting.services.projects.ProjectQueryService
import dk.sdu.cloud.accounting.services.projects.ProjectService
import dk.sdu.cloud.accounting.services.providers.ProviderDao
import dk.sdu.cloud.accounting.services.providers.ProviderIntegrationService
import dk.sdu.cloud.accounting.services.providers.ProviderService
import dk.sdu.cloud.accounting.services.wallets.*
import dk.sdu.cloud.accounting.services.wallets.ProjectCache
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.grant.rpc.GiftController
import dk.sdu.cloud.grant.rpc.GrantController
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.ProjectEvents
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import kotlin.system.exitProcess

class Server(
    override val micro: Micro,
    val config: Configuration
) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val projectCache = ProjectCache(client)
        val verificationService = VerificationService(client)
        val balanceService = BalanceService(projectCache, verificationService, client)
        val visualizationService = VisualizationService(balanceService, projectCache)
        val productService = ProductService(balanceService)

        val favoriteProjects = FavoriteProjectService()
        val eventProducer = micro.eventStreamService.createProducer(ProjectEvents.events)
        val projectService = ProjectService(client, eventProducer)
        val projectGroups = ProjectGroupService(projectService, eventProducer)
        val projectQueryService = ProjectQueryService(projectService)

        val projectsGrants = dk.sdu.cloud.accounting.services.grants.ProjectCache(client)
        val giftService = GiftService(projectsGrants, client)
        val settings = GrantSettingsService(projectsGrants)
        val notifications = GrantNotificationService(projectsGrants, client)
        val grantApplicationService = GrantApplicationService(projectsGrants, settings, notifications, client)
        val templates = GrantTemplateService(projectsGrants, settings)
        val comments = GrantCommentService(grantApplicationService, notifications, projectsGrants)

        val projectsProvider = dk.sdu.cloud.accounting.services.providers.ProjectCache(client)
        val providerDao = ProviderDao(projectsProvider)
        val providerService = ProviderService(db, providerDao, client)
        val providerIntegrationService = ProviderIntegrationService(db, providerService, client,
            micro.developmentModeEnabled)

        if (micro.commandLineArguments.contains("--low-funds-check")) {
            val jobs = LowFundsJob(db, client, config)
            try {
                jobs.notifyLowFundsWallets()
                exitProcess(0)
            } catch (ex: Throwable) {
                log.warn(ex.stackTraceToString())
                exitProcess(1)
            }
        }

        with(micro.server) {
            configureControllers(
                AccountingController(db, balanceService),
                ProductController(db, productService),
                VisualizationController(db, visualizationService),
                Docs(),
                FavoritesController(db, favoriteProjects),
                GiftController(giftService, db),
                GrantController(grantApplicationService, comments, settings, templates, client, db),
                GroupController(db, projectGroups, projectQueryService),
                IntegrationController(providerIntegrationService),
                MembershipController(db, projectQueryService),
                ProjectController(db, projectService, projectQueryService),
                ProviderController(providerService, micro.developmentModeEnabled),
            )
        }

        startServices()
    }
}
