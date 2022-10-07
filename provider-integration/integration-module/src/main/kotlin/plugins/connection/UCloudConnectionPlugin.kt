package dk.sdu.cloud.plugins.connection

import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.RequestContext
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.utils.secureToken
import kotlinx.serialization.*

class UCloudConnectionPlugin : ConnectionPlugin {
    override val pluginTitle: String = "UCloud"
    private lateinit var pluginConfig: ConfigSchema.Plugins.Connection.UCloud

    override fun configure(config: ConfigSchema.Plugins.Connection) {
        this.pluginConfig = config as ConfigSchema.Plugins.Connection.UCloud
    }

    override fun supportsRealUserMode(): Boolean = true
    override fun supportsServiceUserMode(): Boolean = true

    override suspend fun PluginContext.initialize() {
        if (pluginConfig.extensions.onConnectionComplete == null && config.core.launchRealUserInstances) {
            error("$pluginTitle connection plugin does not work with real-user mode without an appropriate extension")
        }
    }

    override suspend fun RequestContext.initiateConnection(username: String): ConnectionResponse {
        try {
            val subject = UCloudSubject(username)
            val extension = pluginConfig.extensions.onConnectionComplete
            val result = if (extension != null) {
                onConnectionComplete.invoke(this, extension, subject)
            } else {
                null
            }
            val token = secureToken(32)

            return ConnectionResponse.Redirect(pluginConfig.redirectTo, token, beforeRedirect = {
                if (result != null) {
                    UserMapping.insertMapping(username, result.uid, this, token)
                }

                IntegrationControl.approveConnection.call(
                    IntegrationControlApproveConnectionRequest(username),
                    rpcClient
                ).orThrow()
            })
        } catch (ex: Throwable) {
            ex.printStackTrace()
            throw ex
        }
    }

    override suspend fun PluginContext.requireMessageSigning(): Boolean =
        pluginConfig.insecureMessageSigningForDevelopmentPurposesOnly

    private companion object Extensions {
        val onConnectionComplete = extension(UCloudSubject.serializer(), UidAndGid.serializer())
    }

    @Serializable
    data class UCloudSubject(
        val username: String,
    )
}

