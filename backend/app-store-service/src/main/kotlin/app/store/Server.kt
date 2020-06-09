package dk.sdu.cloud.app.store

import app.store.services.ApplicationLogoAsyncDAO
import app.store.services.ApplicationPublicAsyncDAO
import app.store.services.ApplicationPublicService
import app.store.services.ApplicationSearchAsyncDAO
import app.store.services.ApplicationSearchService
import app.store.services.ApplicationTagsAsyncDAO
import app.store.services.ApplicationTagsService
import app.store.services.FavoriteAsyncDAO
import app.store.services.FavoriteService
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.AppStoreStreams
import dk.sdu.cloud.app.store.api.ApplicationDescription
import dk.sdu.cloud.app.store.api.ToolDescription
import dk.sdu.cloud.app.store.rpc.AppStoreController
import dk.sdu.cloud.app.store.rpc.ToolController
import dk.sdu.cloud.app.store.services.AppStoreAsyncDAO
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationTable
import dk.sdu.cloud.app.store.services.ElasticDAO
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.PublicDAO
import dk.sdu.cloud.app.store.services.TagTable
import dk.sdu.cloud.app.store.services.ToolHibernateDAO
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val elasticDAO = ElasticDAO(micro.elasticHighLevelClient)
        val toolDAO = ToolHibernateDAO()
        val aclDao = AclHibernateDao()
        val publicDAO = ApplicationPublicAsyncDAO()
        val applicationDAO = AppStoreAsyncDAO(toolDAO, aclDao, publicDAO)
        val appLogoDAO = ApplicationLogoAsyncDAO(applicationDAO)
        val tagDAO = ApplicationTagsAsyncDAO()
        val searchDAO = ApplicationSearchAsyncDAO(applicationDAO)
        val favoriteDAO = FavoriteAsyncDAO(publicDAO, aclDao)

        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val appStoreService = AppStoreService(
            db,
            authenticatedClient,
            applicationDAO,
            publicDAO,
            toolDAO,
            aclDao,
            elasticDAO,
            micro.eventStreamService.createProducer(AppStoreStreams.AppDeletedStream)
        )
        val logoService = LogoService(db, appLogoDAO, toolDAO)
        val tagService = ApplicationTagsService(db, tagDAO, elasticDAO)
        val publicService = ApplicationPublicService(db, publicDAO)
        val searchService = ApplicationSearchService(db, searchDAO, elasticDAO, applicationDAO, authenticatedClient)
        val favoriteService = FavoriteService(db, favoriteDAO, authenticatedClient)

        with(micro.server) {
            configureControllers(
                AppStoreController(appStoreService, logoService, tagService, searchService, publicService, favoriteService),
                ToolController(db, toolDAO, logoService)
            )
        }

        if (micro.developmentModeEnabled) {
            runBlocking {
                val listOfApps = db.withTransaction {
                    applicationDAO.listLatestVersion(it, null, null, emptyList(), NormalizedPaginationRequest(null, null))
                }

                if (listOfApps.itemsInTotal == 0) {
                    val dummyUser = SecurityPrincipal("admin@dev", Role.ADMIN, "admin", "admin", 42000)
                    @Suppress("TooGenericExceptionCaught")
                    db.withTransaction { session ->
                        val tools = File("yaml", "tools")
                        tools.listFiles()?.forEach {
                            try {
                                val description = yamlMapper.readValue<ToolDescription>(it)
                                toolDAO.create(session, dummyUser, description.normalize())
                            } catch (ex: Exception) {
                                log.info("Could not create tool: $it")
                                log.info(ex.stackTraceToString())
                            }
                        }

                        val apps = File("yaml", "apps")
                        apps.listFiles()?.forEach {
                            try {
                                val description = yamlMapper.readValue<ApplicationDescription>(it)
                                applicationDAO.create(session, dummyUser, description.normalize())
                            } catch (ex: Exception) {
                                log.info("Could not create app: $it")
                                log.info(ex.stackTraceToString())
                            }
                        }
                    }
                }
            }
        }

        if (micro.commandLineArguments.contains("--migrate-apps-to-elastic")) {
            @Suppress("TooGenericExceptionCaught")
            try {
                val dummyUser = SecurityPrincipal("admin@dev", Role.ADMIN, "admin", "admin", 42000)
                runBlocking {
                    db.withSession { session ->
                        val apps = applicationDAO.getAllApps(session, dummyUser)
                        apps.forEach { app ->
                            val name = app.getField(ApplicationTable.idName).toLowerCase()
                            val version = app.getField(ApplicationTable.idVersion).toLowerCase()
                            val description = app.getField(ApplicationTable.description).toLowerCase()
                            val title = app.getField(ApplicationTable.title).toLowerCase()
                            val tags = tagDAO.findTagsForApp(
                                session,
                                app.getField(ApplicationTable.idName)
                            ).map { it.getField(TagTable.tag) }

                            elasticDAO.createApplicationInElastic(name, version, description, title, tags)
                            log.info("created: ${app.getField(ApplicationTable.idName)}" +
                                    ":${app.getField(ApplicationTable.idVersion)}"
                            )
                        }
                        log.info("DONE Migrating")
                        exitProcess(0)
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                exitProcess(1)
            }
        }
        startServices()
    }
}
