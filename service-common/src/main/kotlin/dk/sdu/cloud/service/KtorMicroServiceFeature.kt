package dk.sdu.cloud.service

import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.util.AttributeKey

class KtorMicroServiceFeature {
    lateinit var micro: Micro

    private fun validate() {
        if (!this::micro.isInitialized) {
            throw IllegalArgumentException("Micro (variable) has not yet been initialized!")
        }

        if (!micro.initialized) {
            throw IllegalArgumentException("Micro has not yet been initialized!")
        }

        micro.requireFeature(ConfigurationFeature)
        micro.requireFeature(KafkaFeature)
        micro.requireFeature(ServiceInstanceFeature)
        micro.requireFeature(CloudFeature)
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, KtorMicroServiceFeature,
            KtorMicroServiceFeature> {

        override val key: AttributeKey<KtorMicroServiceFeature> = AttributeKey("ktor-micro-service-feature")

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: KtorMicroServiceFeature.() -> Unit
        ): KtorMicroServiceFeature = KtorMicroServiceFeature().also {
            it.configure()
            it.validate()
        }
    }
}
