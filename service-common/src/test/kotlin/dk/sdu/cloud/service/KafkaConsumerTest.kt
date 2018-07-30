package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.admin.NewTopic
import org.junit.Test

class KafkaConsumerTest {
    private val kafkaService = KafkaUtil.createKafkaServices(
        object : ServerConfiguration {
            override val connConfig: ConnectionConfig = ConnectionConfig(
                kafka = KafkaConnectionConfig(
                    listOf(KafkaHostConfig("localhost"))
                ),

                service = ServiceConnectionConfig(
                    description = object : ServiceDescription {
                        override val name: String = "kafka-consumert-test"
                        override val version: String = "1.0.0"
                    },
                    hostname = "localhost",
                    port = -1
                ),

                database = null
            )

            override fun configure() {
                // Do nothing
            }
        },

        createAdminClient = true
    )

    private val adminClient = kafkaService.adminClient!!

    private data class Advanced(
        val id: Int,
        val foo: Pair<String, Int>
    )

    private object Descriptions : KafkaDescriptions() {
        val testStream = stream<Int, Advanced>("kafka-consumer-test-stream") { it.id }
    }

    @Test
    fun testSimpleConsumption() {
        try {
            adminClient.deleteTopics(listOf(Descriptions.testStream.name)).all().get()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        adminClient.createTopics(listOf(NewTopic(Descriptions.testStream.name, 1, 1))).all().get()

        val producer = kafkaService.producer.forStream(Descriptions.testStream)

        val consumedItems = ArrayList<Pair<Int, Advanced>>()

        val consumerThread = Thread {
            val consumer = kafkaService.createConsumer(Descriptions.testStream)
            consumer.forEach { key, value ->
                consumedItems.add(key to value)
            }

            Thread.sleep(5000)
            consumer.close()
        }.also { it.start() }

        val producerJob = launch {
            repeat(50) {
                producer.emit(
                    Advanced(
                        it,
                        "hello" to 42
                    )
                )
            }
        }

        consumerThread.join()
        runBlocking { producerJob.join() }
    }
}