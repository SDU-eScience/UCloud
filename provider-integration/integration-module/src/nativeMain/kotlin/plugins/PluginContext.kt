package dk.sdu.cloud.plugins

import dk.sdu.cloud.IMConfiguration
import dk.sdu.cloud.calls.client.AuthenticatedClient

interface PluginContext {
    val client: AuthenticatedClient
    val config: IMConfiguration
}

class SimplePluginContext(
    override val client: AuthenticatedClient,
    override val config: IMConfiguration,
) : PluginContext