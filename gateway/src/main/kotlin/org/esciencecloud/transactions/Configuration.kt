package org.esciencecloud.transactions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

data class Configuration(
        val kafka: KafkaConfiguration,
        val gateway: GatewayConfiguration,
        val storage: StorageConfiguration
) {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun parseFile(file: File) = mapper.readValue<Configuration>(file)
    }
}

data class KafkaConfiguration(val servers: List<String>)
data class GatewayConfiguration(val port: Int)
data class StorageConfiguration(val host: String, val port: Int)
