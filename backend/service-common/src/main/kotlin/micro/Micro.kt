package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.FrontendOverrides
import org.apache.logging.log4j.core.config.ConfigurationFactory
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

//typealias Micro = Micro

fun <T : MicroFeature> Micro.feature(factory: MicroFeatureFactory<T, *>): T {
    return featureOrNull(factory) ?: throw NoSuchElementException("Feature not initialized!")
}

fun <T : MicroFeature> Micro.featureOrNull(factory: MicroFeatureFactory<T, *>): T? {
    return getOrNull(factory.key)
}

fun Micro.requireFeature(factory: MicroFeatureFactory<*, *>) {
    featureOrNull(factory) ?: throw IllegalStateException("$factory is a required feature")
}

@Deprecated("Replace with Service")
fun Micro.init(description: ServiceDescription, cliArgs: Array<String>) {
    isEmbeddedService = false
    serviceDescription = description
    commandLineArguments = cliArgs.toList()
}

@OptIn(ExperimentalTime::class)
fun <Feature : MicroFeature, Config> Micro.install(
    featureFactory: MicroFeatureFactory<Feature, Config>,
    configuration: Config
) {
    if (getOrNull(featureFactory.key) != null) return

    val time = measureTime {
        val feature = featureFactory.create(configuration)
        // Must happen before init to ensure that requireFeature(self) will not fail
        attributes[featureFactory.key] = feature

        feature.init(this, serviceDescription, commandLineArguments)
    }

    // TODO Logging hasn't been initialized yet
    println("Installing feature: ${featureFactory.key.name}. Took: ${time}")
}

fun <T : Any> delegate(key: String): ReadWriteProperty<Micro, T> {
    val microKey = MicroAttributeKey<T>(key)
    return object : ReadWriteProperty<Micro, T>  {
        override fun getValue(thisRef: Micro, property: KProperty<*>): T {
            return thisRef[microKey]
        }

        override fun setValue(thisRef: Micro, property: KProperty<*>, value: T) {
            thisRef[microKey] = value
        }
    }
}

var Micro.serviceDescription: ServiceDescription by delegate("serviceDescription")
var Micro.commandLineArguments: List<String> by delegate("commandLineArguments")

class Micro(private val parent: Micro? = null) {
    private val internalAttributes = HashMap<String, Any>()
    private val children = ArrayList<Micro>()

    val attributes = this // Backwards compatibility
    init {
        // Hack for backwards compatibility
        if (parent == null) {
            ConfigurationFactory.setConfigurationFactory(Log4j2ConfigFactory)
        }
    }

    fun createScope(): Micro {
        val scope = Micro(this)
        children.add(scope)
        return scope
    }

    internal fun clear() {
        internalAttributes.clear()
    }

    fun all(): Map<String, Any> {
        return internalAttributes.toMap()
    }

    operator fun <T : Any> set(key: MicroAttributeKey<T>, value: T) {
        internalAttributes[key.name] = value
    }

    operator fun <T : Any> get(key: MicroAttributeKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return getOrNull(key) ?: throw IllegalStateException("Missing attribute for key: ${key.name}")
    }

    fun <T : Any> getOrNull(key: MicroAttributeKey<T>): T? {
        @Suppress("UNCHECKED_CAST") val result = internalAttributes[key.name] as T?

        if (result == null && parent != null) {
            return parent.getOrNull(key)
        }

        return result
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
    install(LogFeature)
    install(KtorServerProviderFeature)
    install(RedisFeature)
    install(ClientFeature)
    install(TokenValidationFeature)
    install(ServerFeature)
    install(FrontendOverrides)
    install(DatabaseConfigurationFeature)
    install(FlywayFeature)
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
