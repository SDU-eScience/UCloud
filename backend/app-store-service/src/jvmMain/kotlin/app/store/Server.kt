package dk.sdu.cloud.app.store

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.fasterxml.jackson.module.kotlin.readValue
import dk.cloud.sdu.app.store.rpc.AppLogoController
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.store.rpc.AppFavoriteController
import dk.sdu.cloud.app.store.rpc.AppPublicController
import dk.sdu.cloud.app.store.rpc.AppSearchController
import dk.sdu.cloud.app.store.rpc.AppStoreController
import dk.sdu.cloud.app.store.rpc.AppTagController
import dk.sdu.cloud.app.store.rpc.ToolController
import dk.sdu.cloud.app.store.services.AppStoreAsyncDao
import dk.sdu.cloud.app.store.services.AppStoreService
import dk.sdu.cloud.app.store.services.ApplicationLogoAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationPublicAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationPublicService
import dk.sdu.cloud.app.store.services.ApplicationSearchAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationSearchService
import dk.sdu.cloud.app.store.services.ApplicationTable
import dk.sdu.cloud.app.store.services.ApplicationTagsAsyncDao
import dk.sdu.cloud.app.store.services.ApplicationTagsService
import dk.sdu.cloud.app.store.services.ElasticDao
import dk.sdu.cloud.app.store.services.FavoriteAsyncDao
import dk.sdu.cloud.app.store.services.FavoriteService
import dk.sdu.cloud.app.store.services.LogoService
import dk.sdu.cloud.app.store.services.TagTable
import dk.sdu.cloud.app.store.services.ToolAsyncDao
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.app.store.util.yamlMapper
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import kotlin.system.exitProcess

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val elasticDAO = ElasticDao(micro.elasticHighLevelClient)
        val toolDAO = ToolAsyncDao()
        val aclDao = AclAsyncDao()
        val publicDAO = ApplicationPublicAsyncDao()
        val applicationDAO = AppStoreAsyncDao(toolDAO, aclDao, publicDAO)
        val appLogoDAO = ApplicationLogoAsyncDao(applicationDAO)
        val tagDAO = ApplicationTagsAsyncDao()
        val searchDAO = ApplicationSearchAsyncDao(applicationDAO)
        val favoriteDAO = FavoriteAsyncDao(publicDAO, aclDao)

        val db = AsyncDBSessionFactory(micro)
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

        configureJackson(ApplicationParameter::class, yamlMapper)

        with(micro.server) {
            configureControllers(
                AppStoreController(appStoreService),
                ToolController(db, toolDAO, logoService),
                AppLogoController(logoService),
                AppTagController(tagService),
                AppSearchController(searchService),
                AppPublicController(publicService),
                AppFavoriteController(favoriteService)
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
                                toolDAO.create(session, dummyUser, description.normalize(), "original")
                            } catch (ex: Exception) {
                                log.info("Could not create tool: $it")
                                log.info(ex.stackTraceToString())
                            }
                        }

                        val apps = File("yaml", "apps")
                        apps.listFiles()?.forEach {
                            try {
                                val description = yamlMapper.readValue<ApplicationDescription>(it)
                                applicationDAO.create(session, dummyUser, description.normalize(), "original")
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

        if (micro.commandLineArguments.contains("--resize-logos")) {
            runBlocking {
                try {
                    logoService.resizeAll()
                    exitProcess(0)
                } catch (ex: Throwable) {
                    ex.printStackTrace()
                    exitProcess(1)
                }
            }
        }
        startServices()
    }
}


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
abstract class SealedClassMixin

fun configureJackson(callContainer: CallDescriptionContainer, mapper: ObjectMapper) {
    callContainer.callContainer.forEach { call ->
        // TODO Need to traverse the types for generics
        // TODO need to traverse dependencies also
        val requestClassifier = call.requestClass.classifier
        if (requestClassifier is KClass<*>) configureJackson(requestClassifier, mapper)

        val successClassifier = call.successClass.classifier
        if (successClassifier is KClass<*>) configureJackson(successClassifier, mapper)

        val errorClassifier = call.errorClass.classifier
        if (errorClassifier is KClass<*>) configureJackson(errorClassifier, mapper)
    }
}

private fun configureJackson(klass: KClass<*>, mapper: ObjectMapper) {
    val javaClass = klass.java

    if (klass.isSealed) {
        mapper.addMixIn(javaClass, SealedClassMixin::class.java)
        klass.sealedSubclasses.forEach {
            val name = it.annotations.filterIsInstance<SerialName>().firstOrNull()?.value ?: it.qualifiedName
            ?: it.jvmName
            mapper.registerSubtypes(NamedType(it.java, name))
        }
    }
}
