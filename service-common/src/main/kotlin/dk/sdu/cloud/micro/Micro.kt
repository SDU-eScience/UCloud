package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.FrontendOverrides
import dk.sdu.cloud.service.Loggable
import kotlin.system.measureTimeMillis

class Micro : Loggable {
    override val log = logger()

    var initialized: Boolean = false
        private set

    var commandLineArguments: List<String> = emptyList()
        private set

    lateinit var serviceDescription: ServiceDescription
        private set

    val attributes = MicroAttributes()
    private val featureRegistryKey =
        MicroAttributeKey<MicroAttributes>("feature-registry")

    val featureRegistry: MicroAttributes get() = attributes[featureRegistryKey]

    fun <T : MicroFeature> feature(factory: MicroFeatureFactory<T, *>): T {
        return attributes[featureRegistryKey][factory.key]
    }

    fun <T : MicroFeature> featureOrNull(factory: MicroFeatureFactory<T, *>): T? {
        return attributes[featureRegistryKey].getOrNull(factory.key)
    }

    fun requireFeature(factory: MicroFeatureFactory<*, *>) {
        featureOrNull(factory) ?: throw IllegalStateException("$factory is a required feature")
    }

    fun <Feature : MicroFeature, Config> install(
        featureFactory: MicroFeatureFactory<Feature, Config>,
        configuration: Config
    ) {
        val time = measureTimeMillis {
            if (!initialized) throw IllegalStateException("Call init() before installing features")

            val feature = featureFactory.create(configuration)
            // Must happen before init to ensure that requireFeature(self) will not fail
            attributes[featureRegistryKey][featureFactory.key] = feature

            feature.init(this, serviceDescription, commandLineArguments)
        }

        log.info("Installing feature: ${featureFactory.key.name}. Took: ${time}ms")
    }

    fun init(description: ServiceDescription, cliArgs: Array<String>) {
        attributes.clear()
        this.serviceDescription = description
        commandLineArguments = cliArgs.toList()

        attributes[featureRegistryKey] = MicroAttributes()
        initialized = true
    }
}

class MicroAttributes {
    private val attributes = HashMap<String, Any>()

    internal fun clear() {
        attributes.clear()
    }

    fun all(): Map<String, Any> {
        return attributes.toMap()
    }

    operator fun <T : Any> set(key: MicroAttributeKey<T>, value: T) {
        attributes[key.name] = value
    }

    operator fun <T : Any> get(key: MicroAttributeKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return attributes[key.name] as? T ?: throw IllegalStateException("Missing attribute for key: ${key.name}")
    }

    fun <T : Any> getOrNull(key: MicroAttributeKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return (attributes[key.name] ?: return null) as T
    }
}

@Suppress("unused")
data class MicroAttributeKey<T : Any>(val name: String)

interface MicroFeature {
    fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>)
}

interface MicroFeatureFactory<Feature : MicroFeature, Config> {
    val key: MicroAttributeKey<Feature>

    fun create(config: Config): Feature
}

fun Micro.installDefaultFeatures() {
    install(DeinitFeature)
    install(ScriptFeature)
    install(ConfigurationFeature)
    install(ServiceDiscoveryOverrides)
    install(ServiceInstanceFeature)
    install(DevelopmentOverrides)
    install(KtorServerProviderFeature)
    install(RedisFeature)
    install(ClientFeature)
    install(TokenValidationFeature)
    install(ServerFeature)
    install(FrontendOverrides)
}

fun Micro.initWithDefaultFeatures(
    description: ServiceDescription,
    cliArgs: Array<String>
) {
    init(description, cliArgs)
    installDefaultFeatures()
}

fun <Feature : MicroFeature> Micro.install(factory: MicroFeatureFactory<Feature, Unit>) {
    install(factory, Unit)
}
