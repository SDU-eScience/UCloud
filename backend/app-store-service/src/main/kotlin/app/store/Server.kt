package dk.sdu.cloud.app.store

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationDescription
import dk.sdu.cloud.app.store.api.ToolDescription
import dk.sdu.cloud.app.store.rpc.AppStoreController
import dk.sdu.cloud.app.store.rpc.ToolController
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationHibernateDAO
import dk.sdu.cloud.app.store.services.ElasticDAO
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.ToolHibernateDAO
import dk.sdu.cloud.app.store.services.acl.AclHibernateDao
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.configureControllers
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
        val applicationDAO = ApplicationHibernateDAO(toolDAO, aclDao)

        val db = micro.hibernateDatabase
        val authenticatedClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val appStoreService = AppStoreService(db, authenticatedClient, applicationDAO, toolDAO, aclDao, elasticDAO)
        val logoService = LogoService(db, applicationDAO, toolDAO)

        with(micro.server) {
            configureControllers(
                AppStoreController(appStoreService, logoService),
                ToolController(db, toolDAO, logoService)
            )
        }

        if (micro.developmentModeEnabled) {
            runBlocking {
                val listOfApps = db.withTransaction {
                    applicationDAO.listLatestVersion(it, null, NormalizedPaginationRequest(null, null))
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
                    micro.hibernateDatabase.withTransaction { session ->
                        val apps = applicationDAO.getAllApps(session, dummyUser)
                        apps.forEach { app ->
                            val name = app.id.name.toLowerCase()
                            val version = app.id.version.toLowerCase()
                            val description = app.description.toLowerCase()
                            val title = app.title.toLowerCase()
                            val tags = applicationDAO.findTagsForApp(session, app.id.name).map { it.tag }

                            elasticDAO.createApplicationInElastic(name, version, description, title, tags)
                            log.info("created: ${app.id.name}:${app.id.version}")
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
