package dk.sdu.cloud

import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.kubernetes.monitor.KubernetesMonitorService
import dk.sdu.cloud.micro.Service
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.redis.cleaner.RedisCleanerService
import dk.sdu.cloud.service.ClassDiscovery
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.webdav.WebdavService

object Launcher : Loggable {
    override val log = logger()
}

suspend fun main(args: Array<String>) {
    if (args.contains("--run-script") && args.contains("migrate-db")) {
        println("REFUSING TO START LAUNCHER WHEN MIGRATING DB")
        return
    }

    val reg = ServiceRegistry(args)
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

    ClassDiscovery(listOf("dk.sdu.cloud"), Launcher::class.java.classLoader) {
        val objectInstance = it.objectInstance
        if (objectInstance != null) {
            if (objectInstance is Service) {
                if (objectInstance !in blacklist) {
                    try {
                        Launcher.log.info("Registering ${objectInstance.javaClass.canonicalName}")
                        reg.register(objectInstance)
                    } catch (ex: Throwable) {
                        Launcher.log.error("Caught error: ${ex.stackTraceToString()}")
                    }
                }
            }
        }
    }.detect()

    reg.start()
}