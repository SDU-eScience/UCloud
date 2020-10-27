package dk.sdu.cloud

import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.kubernetes.monitor.KubernetesMonitorService
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.redis.cleaner.RedisCleanerService
import dk.sdu.cloud.service.ClassDiscovery
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.webdav.WebdavService
import org.apache.logging.log4j.Level
import kotlin.system.exitProcess

object Launcher : Loggable {
    override val log = logger()
}

suspend fun main(args: Array<String>) {
    if (args.contains("--run-script") && args.contains("migrate-db")) {
        val micro = Micro().apply {
            initWithDefaultFeatures(object : ServiceDescription {
                override val name: String = "launcher"
                override val version: String = "1"
            }, args)
        }

        micro.databaseConfig.migrateAll()
        exitProcess(0)
    }

    val reg = ServiceRegistry(args, PlaceholderServiceDescription)
    val blacklist = setOf(
        // WebDav needs to run as a standalone server
        WebdavService,

        // The following 'services' are all essentially scripts that run in UCloud
        // None of them are meant to be run as a normal service.
        //AuditIngestionService,
        RedisCleanerService,
        ElasticManagementService,
        KubernetesMonitorService
    )

    val loader = Launcher::class.java.classLoader

    ClassDiscovery(listOf("dk.sdu.cloud"), Launcher::class.java.classLoader) {
        val objectInstance = it.objectInstance
        if (objectInstance != null) {
            if (objectInstance is Service) {
                if (objectInstance !in blacklist) {
                    try {
                        Launcher.log.trace("Registering ${objectInstance.javaClass.canonicalName}")
                        reg.register(objectInstance)
                    } catch (ex: Throwable) {
                        Launcher.log.error("Caught error: ${ex.stackTraceToString()}")
                    }
                }
            }
        }
    }.detect()

    reg.rootMicro.feature(LogFeature).configureLevels(mapOf(
        // Don't display same information twice
        "dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor" to Level.INFO
    ))

    reg.start()
}
