package dk.sdu.cloud.plugins

interface Plugin<ConfigType> {
    val pluginTitle: String
    fun configure(config: ConfigType) {}
    suspend fun PluginContext.initialize() {}
}

