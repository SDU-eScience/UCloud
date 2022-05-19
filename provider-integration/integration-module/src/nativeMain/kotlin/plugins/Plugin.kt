package dk.sdu.cloud.plugins

interface Plugin<ConfigType> {
    fun configure(config: ConfigType) {}
    suspend fun PluginContext.initialize() {}
}

