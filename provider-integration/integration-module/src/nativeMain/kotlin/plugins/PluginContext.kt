package dk.sdu.cloud.plugins

import dk.sdu.cloud.calls.client.AuthenticatedClient

interface PluginContext {
    val client: AuthenticatedClient
}

class SimplePluginContext(
    override val client: AuthenticatedClient
) : PluginContext