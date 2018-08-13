package dk.sdu.cloud.service

import com.github.zafarkhaja.semver.Version
import dk.sdu.cloud.client.ServiceDescription

fun ServiceDescription.definition(): ServiceDefinition = ServiceDefinition(name, Version.valueOf(version))
fun ServiceDescription.instance(config: ConnectionConfig): ServiceInstance =
    ServiceInstance(definition(), config.service.hostname, config.service.port)

data class ServiceDefinition(val name: String, val version: Version)
data class ServiceInstance(val definition: ServiceDefinition, val hostname: String, val port: Int)
