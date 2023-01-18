package dk.sdu.cloud.service

import com.fasterxml.jackson.annotation.JsonProperty
import dk.sdu.cloud.ServiceDescription
import kotlinx.serialization.Serializable

fun ServiceDescription.definition(): ServiceDefinition = ServiceDefinition(name, version)

@Serializable
data class ElasticServiceDefinition(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("version")
    val version: String)


@Serializable
data class ElasticServiceInstance(
    @JsonProperty("definition")
    val definition: ElasticServiceDefinition,
    @JsonProperty("hostname")
    val hostname: String,
    @JsonProperty("port")
    val port: Int,
    @JsonProperty("ipAddress")
    val ipAddress: String? = null
)

@Serializable
data class ServiceDefinition(
    val name: String,
    val version: String)


@Serializable
data class ServiceInstance(
    val definition: ServiceDefinition,
    val hostname: String,
    val port: Int,
    val ipAddress: String? = null
)
