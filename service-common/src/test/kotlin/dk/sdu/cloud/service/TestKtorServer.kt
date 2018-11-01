package dk.sdu.cloud.service

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.SDUCloud
import dk.sdu.cloud.client.ServiceDescription
import dk.sdu.cloud.client.bindEntireRequestFromBody
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.*
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

data class Echo(val message: String)

class UnauthenticatedCloud(
    override val parent: CloudContext,
    private val handler: HttpRequestBuilder.() -> Unit
) : AuthenticatedCloud {
    override fun HttpRequestBuilder.configureCall() {
        handler()
    }
}


fun main(args: Array<String>) {
    val cloud = UnauthenticatedCloud(SDUCloud("http://localhost:8080")) {
        header("Job-Id", UUID.randomUUID().toString())
        header(HttpHeaders.XForwardedFor, "123.123.123.123")
    }

    val producer = mockk<KafkaProducer<String, String>>(relaxed = true)
    val kafka = KafkaServices(
        mockk(relaxed = true),
        mockk(relaxed = true),
        producer,
        mockk(relaxed = true)
    )

    val future = FutureTask<RecordMetadata> {
        mockk(relaxed = true)
    }

    val producerRecord: MutableList<ProducerRecord<String, String>> = ArrayList()
    every { producer.send(capture(producerRecord), any()) } answers {
        val callback = call.invocation.args[1] as Callback
        val metadata = mockk<RecordMetadata>(relaxed = true)
        callback.onCompletion(metadata, null)
        future
    }

    val description = object : ServiceDescription {
        override val name: String = "test"
        override val version: String = "1.0.0"
    }.definition()

    val instance = ServiceInstance(description, "localhost", 8080)

    val rest = object : RESTDescriptions("foo") {
        val call = callDescription<Echo, Echo, Unit> {
            name = "call"
            method = HttpMethod.Post

            path {
                +"foo"
            }

            auth {
                roles = Roles.PUBLIC
                access = AccessRight.READ
            }


            body { bindEntireRequestFromBody() }
        }
    }

    val server = embeddedServer(Netty, port = 8080) {
        installDefaultFeatures(cloud, kafka, instance, false)

        routing {
            implement(rest.call) {
                ok(it)
            }
        }
    }

    server.start()

    val result = runBlocking {
        rest.call.call(Echo("foo"), cloud)
    }

    println(result)
    println(producerRecord)
    server.stop(0, 0, TimeUnit.SECONDS)
}

