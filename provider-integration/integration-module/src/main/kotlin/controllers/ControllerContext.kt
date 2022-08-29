package dk.sdu.cloud.controllers

import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.plugins.PluginContext

class ControllerContext(
    val ownExecutable: String,
    val configuration: VerifiedConfig,
    val pluginContext: PluginContext,
)
