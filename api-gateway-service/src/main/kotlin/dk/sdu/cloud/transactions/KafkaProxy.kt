package dk.sdu.cloud.transactions

import dk.sdu.cloud.service.*
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route
import io.ktor.routing.route
import org.apache.kafka.clients.producer.KafkaProducer
import dk.sdu.cloud.client.GatewayJobResponse
import org.slf4j.LoggerFactory

class KafkaProxy(val targets: List<ServiceDefinition>, val producer: KafkaProducer<String, String>) {
    fun configure(route: Route): Unit = with(route) {
        // NOTE: Do _NOT_ replace with .forEach { ... } as this will break the code.
        for (service in targets) {
            for (mapping in service.kafkaDescriptions.flatMap { it.descriptions }) {
                for (target in mapping.targets) {
                    @Suppress("UNCHECKED_CAST")
                    val producer = EventProducer(
                            producer,
                            SimpleStreamDescription(mapping.topicName, mapping.keySerde, mapping.valueSerde)
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

    companion object {
        private val log = LoggerFactory.getLogger(KafkaProxy::class.java)
    }
}
