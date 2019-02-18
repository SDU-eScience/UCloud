package dk.sdu.cloud.service

import dk.sdu.cloud.ServiceDescription

fun ServiceDescription.definition(): ServiceDefinition = ServiceDefinition(name, version)

data class ServiceDefinition(val name: String, val version: String)
data class ServiceInstance(
    val definition: ServiceDefinition,
    val hostname: String,
    val port: Int,
    val ipAddress: String? = null
)
