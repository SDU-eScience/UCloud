package dk.sdu.cloud.service

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.kafka.KafkaDescriptions
import dk.sdu.cloud.micro.ConfigurationFeature
import dk.sdu.cloud.micro.KafkaFeature
import dk.sdu.cloud.micro.KafkaFeatureConfiguration
import dk.sdu.cloud.micro.KafkaTopicFeature
import dk.sdu.cloud.micro.KafkaTopicFeatureConfiguration
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.install
import org.junit.Ignore
import org.junit.Test

object Streams : KafkaDescriptions() {
    val simpleStream = stream<String, Pair<Int, Int>>("testing")
    val auditFiles = stream<String, String>("audit.files")
}

class KafkaTopicDiscovery {
    @Ignore
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
            install(
                KafkaTopicFeature, KafkaTopicFeatureConfiguration(
                    basePackages = listOf("dk.sdu.cloud.service")
                )
            )
        }
    }
}
