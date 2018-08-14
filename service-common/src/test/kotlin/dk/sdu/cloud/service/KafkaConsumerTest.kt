package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KafkaConsumerTest {
    private val kafkaService = run {
        val micro = Micro().apply {
            install(ConfigurationFeature)
            install(KafkaFeature, KafkaFeatureConfiguration())

            val configFile = Files.createTempFile("config", ".json").toFile().also {
                //language=json
                it.writeText(
                    """
                    {
                        "kafka": {
                          "brokers": { "hostname": "localhost" }
                        }
                    }
                    """.trimIndent()
                )
            }

            feature(ConfigurationFeature).injectFile(configuration, configFile)

            val description = object : ServiceDescription {
                override val name: String = "kafka-consumer-test"
                override val version: String = "1.0.0"
            }

            init(description, emptyArray())
        }

        micro.kafka
    }

    private val adminClient = kafkaService.adminClient

    private data class Advanced(
        val id: Int,
        val foo: Pair<String, Int>
    )

    private object Descriptions : KafkaDescriptions() {
        val testStream = stream<String, Advanced>("kafka-consumer-test-stream") { it.id.toString() }
    }

    @Test
    fun testSimpleConsumption() {
        /*
        try {
            adminClient.deleteTopics(listOf(Descriptions.testStream.name)).all().get()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        adminClient.createTopics(listOf(NewTopic(Descriptions.testStream.name, 1, 1))).all().get()
        */

        val producer = kafkaService.producer.forStream(Descriptions.testStream)
        val consumedItems = Array(16) { ArrayList<Pair<String, Advanced>>() }
        val consumers = (0 until 16).map { id ->
            val start = System.currentTimeMillis()
            var lastTimer = System.currentTimeMillis()
            fun timeSinceStart(): Long =
                (System.currentTimeMillis() - start).also { lastTimer = System.currentTimeMillis() }

            fun delta(): Long = System.currentTimeMillis() - lastTimer

            kafkaService.createConsumer(Descriptions.testStream).configure { root ->
                root
                    .batched(batchTimeout = 100, maxBatchSize = 10)
                    .consumeBatchAndCommit {
                        println("[$id] Consumed ${it.size} items. ${delta()} since last. ${timeSinceStart()} ms after start")
                        consumedItems[id].addAll(it)
                    }
            }
        }

        val producerJob = launch {
            delay(2000)

            repeat(1000) {
                producer.emit(Advanced(it, "hello" to 42))
            }
        }
        runBlocking { producerJob.join() }

        Thread.sleep(10000)
        consumers.forEach { it.close() }

        println(consumedItems)
        assertEquals(
            1000,
            // We have at-least-once delivery. But we still want to make sure all of them are actually delivered
            consumedItems.flatMap { it }.associateBy { it.first }.values.size
        )
    }

    @Test
    fun testLongRunningBatch() {
        var caughtException: Throwable? = null

        val producer = kafkaService.producer.forStream(Descriptions.testStream)
        val consumer = kafkaService.createConsumer(Descriptions.testStream).configure { root ->
            root
                .batched(batchTimeout = 30_000, maxBatchSize = Int.MAX_VALUE)
                .consumeBatchAndCommit {
                    println("Consumed a batch of size: ${it.size}")
                    assertEquals(2000, it.size)
                }
        }

        consumer.onExceptionCaught { caughtException = it }

        val producerJob = launch {
            repeat(2000) { producer.emit(Advanced(it, "hello" to 42)) }
        }
        runBlocking { producerJob.join() }

        repeat(35) { println(it); Thread.sleep(1000) }
        consumer.close()

        if (caughtException != null) throw caughtException!!
    }

    @Test
    fun testOverflow() {
        var caughtException: Throwable? = null

        val processedIds = hashSetOf<Int>()
        val producer = kafkaService.producer.forStream(Descriptions.testStream)
        val consumer = kafkaService.createConsumer(Descriptions.testStream, internalQueueSize = 1).configure { root ->
            root.consumeAndCommit {
                processedIds.add(it.second.id)
                Thread.sleep(10)
            }
        }

        consumer.onExceptionCaught { caughtException = it }

        val producerJob = launch {
            repeat(2000) { producer.emit(Advanced(it, "hello" to 42)) }
        }
        runBlocking { producerJob.join() }

        // Might want to increase this number. Being able to finish in 35 seconds actually requires a bit of luck from
        // the locks
        repeat(35) { println(it); Thread.sleep(1000) }
        consumer.close()

        repeat(2000) { assertTrue(it in processedIds, "Expected $it in processedIds") }
        assertEquals(2000, processedIds.size)

        if (caughtException != null) throw caughtException!!
    }
}