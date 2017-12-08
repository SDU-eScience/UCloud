package org.esciencecloud.transactions.new

import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.apache.kafka.clients.producer.KafkaProducer
import org.esciencecloud.client.GatewayJobResponse
import org.esciencecloud.service.*
import org.slf4j.LoggerFactory

class KafkaProxy(val targets: List<ServiceDefinition>, val producer: KafkaProducer<String, String>) {
    companion object {
        private val log = LoggerFactory.getLogger(KafkaProxy::class.java)
    }

    fun configure(route: Route): Unit = with(route) {
        // NOTE: Do _NOT_ replace with .forEach { ... } as this will break the code.
        for (service in targets) {
            for (mapping in service.kafkaDescriptions.flatMap { it.descriptions }) {
                for (target in mapping.targets) {
                    @Suppress("UNCHECKED_CAST")
                    val producer = EventProducer(
                            producer,
                            StreamDescription(mapping.topicName, mapping.keySerde, mapping.valueSerde)
                    ) as EventProducer<Any, Any>

                    route(target.path.basePath) {
                        implement(target) { request ->
                            @Suppress("UNCHECKED_CAST")
                            proxyKafka(mapping as KafkaMappingDescription<Any, Any, Any>, producer, request)
                        }
                    }
                }
            }
        }
    }

    // TODO Something wrong with types...
    /*
    private suspend fun <R : Any, K : Any, V : Any> RESTHandler<R, GatewayJobResponse, GatewayJobResponse>.proxyKafka(
            mappingDescription: KafkaMappingDescription<R, K, V>,
            producer: EventProducer<K, V>,
            request: R
    ) {
    */
    private suspend fun RESTHandler<*, GatewayJobResponse, GatewayJobResponse>.proxyKafka(
            mappingDescription: KafkaMappingDescription<Any, Any, Any>,
            producer: EventProducer<Any, Any>,
            request: Any
    ) {
        val header = call.validateRequestAndPrepareJobHeader(respond = false) ?:
                return error(GatewayJobResponse.error(), HttpStatusCode.BadRequest)

        val wrappedRequest = KafkaRequest(header, request)
        val (key, value) = try {
            mappingDescription.mappper(wrappedRequest)
        } catch (ex: IllegalArgumentException) {
            return error(GatewayJobResponse.error(), HttpStatusCode.BadRequest)
        } catch (ex: Exception) {
            return error(GatewayJobResponse.error(), HttpStatusCode.InternalServerError)
        }

        val result = producer.emit(key, value)
        ok(GatewayJobResponse.started(header.uuid, result.offset(), result.partition(), result.timestamp()))
    }
}