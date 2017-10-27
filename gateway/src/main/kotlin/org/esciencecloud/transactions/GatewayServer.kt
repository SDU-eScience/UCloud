package org.esciencecloud.transactions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.esciencecloud.kafka.examples.MetadataKey
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.jetbrains.ktor.application.install
import org.jetbrains.ktor.gson.GsonSupport
import org.jetbrains.ktor.host.embeddedServer
import org.jetbrains.ktor.netty.Netty
import org.jetbrains.ktor.request.receive
import org.jetbrains.ktor.response.respond
import org.jetbrains.ktor.routing.post
import org.jetbrains.ktor.routing.put
import org.jetbrains.ktor.routing.route
import org.jetbrains.ktor.routing.routing
import org.slf4j.LoggerFactory
import java.util.*

typealias StoragePath = String
data class User(val username: String, val password: String)
data class PermissionChange(val right: String, val to: String)
data class BulkPermissionChange(val on: StoragePath, val changes: List<PermissionChange>)
data class PermissionRequest(val user: User, val permissions: List<BulkPermissionChange>)

data class MetadataUpdateRequest(val path: String, val key: String, val value: String)

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Server")!!
    val producer = KafkaProducer<String, JsonNode>(mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.connect.json.JsonSerializer"
    ))

    val mapper = jacksonObjectMapper()

    embeddedServer(Netty, port = 8080) {
        install(GsonSupport)

        routing {
            route("/api/") {
                put("permissions") {
                    val request = call.receive<PermissionRequest>()
                    val jobId = UUID.randomUUID()
                    log.info(request.toString())
                    producer.send(ProducerRecord(
                            "bulk-permission-request",
                            jobId.toString(),
                            mapper.valueToTree(request))
                    )

                    call.respond(mapOf(
                            "status" to "started",
                            "jobId" to jobId)
                    )
                }

                post("metadata") {
                    val request = call.receive<MetadataUpdateRequest>()
                    val key = mapper.writeValueAsString(MetadataKey(request.path, request.key))
                    producer.send(ProducerRecord(
                            "requests.metadata.update",
                            key,
                            mapper.valueToTree(request.value)
                    ))

                    call.respond(mapOf(
                            "status" to "started"
                    ))
                }
            }
        }
    }.start(wait = true)

    producer.close()
}

