package dk.sdu.cloud.k8

data class ConfigBlock(val prefix: String, val fn: ConfigurationContext.(ctx: DeploymentContext) -> Unit)

object Configuration {
    private val internalMap = HashMap<String, Any>()
    val blocks = ArrayList<ConfigBlock>()

    fun <T : Any> retrieve(key: String, documentation: String, defaultValue: T? = null): T {
        @Suppress("UNCHECKED_CAST")
        return internalMap[key] as? T ?: defaultValue
        ?: throw IllegalStateException("Missing configuration at: $key\n$documentation")
    }

    fun configure(key: String, value: Any) {
        internalMap[key] = value
    }

    fun runBlocks(ctx: DeploymentContext) {
        blocks.forEach { it.fn(ConfigurationContext(it.prefix), ctx) }
    }
}

data class ConfigurationContext(val prefix: String) {
    fun configure(key: String, value: Any) {
        Configuration.configure("$prefix.$key", value)
    }
}

fun config(prefix: String, block: ConfigurationContext.(ctx: DeploymentContext) -> Unit) {
    Configuration.blocks.add(ConfigBlock(prefix, block))
}

fun <T : Any> MutableBundle.config(key: String, documentation: String, defaultValue: T? = null): T {
    return Configuration.retrieve<T>("$name.$key", documentation, defaultValue)
}