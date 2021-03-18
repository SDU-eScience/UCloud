package dk.sdu.cloud.service

import dk.sdu.cloud.ServiceDescription
import kotlinx.serialization.Serializable

fun ServiceDescription.definition(): ServiceDefinition = ServiceDefinition(name, version)

@Serializable
data class ServiceDefinition(val name: String, val version: String)

@Serializable
data class ServiceInstance(
    val definition: ServiceDefinition,
    val hostname: String,
    val port: Int,
    val ipAddress: String? = null
)
