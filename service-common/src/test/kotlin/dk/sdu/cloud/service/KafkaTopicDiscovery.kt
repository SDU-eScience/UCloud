package dk.sdu.cloud.service

import dk.sdu.cloud.client.ServiceDescription
import org.junit.Test

object Streams : KafkaDescriptions() {
    val simpleStream = stream<String, Pair<Int, Int>>("testing")
    val auditFiles = stream<String, String>("audit.files")
}

class KafkaTopicDiscovery {
    @Test
    fun simpleTest() {
        val description = object : ServiceDescription {
            override val name: String = "foo"
            override val version: String = "1.0.0"
        }

        val micro = Micro().apply {
            init(description, emptyArray())
            install(ConfigurationFeature)
            install(KafkaFeature, KafkaFeatureConfiguration())
            install(KafkaTopicFeature, KafkaTopicFeatureConfiguration(
                basePackages = listOf("dk.sdu.cloud.service")
            ))
        }
    }
}
