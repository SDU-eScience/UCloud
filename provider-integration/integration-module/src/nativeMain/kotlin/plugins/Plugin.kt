package dk.sdu.cloud.plugins

interface Plugin<ConfigType> {
    suspend fun PluginContext.initialize(pluginConfig: ConfigType) {}
}
