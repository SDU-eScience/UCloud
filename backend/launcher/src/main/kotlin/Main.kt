package dk.sdu.cloud

import dk.sdu.cloud.audit.ingestion.AuditIngestionService
import dk.sdu.cloud.elastic.management.ElasticManagementService
import dk.sdu.cloud.file.gateway.FileGatewayService
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
    val reg = ServiceRegistry(args)
    val blacklist = setOf(
        WebdavService,
        AuditIngestionService,
        FileGatewayService,
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