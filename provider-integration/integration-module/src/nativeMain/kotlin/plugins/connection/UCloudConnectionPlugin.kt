package dk.sdu.cloud.plugins.connection

import dk.sdu.cloud.callBlocking
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.UserMapping
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.plugins.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.sql.*
import kotlinx.serialization.*

class UCloudConnectionPlugin : ConnectionPlugin {
    private lateinit var pluginConfig: UCloudConnectionConfiguration

    override fun configure(config: ConfigSchema.Plugins.Connection) {
        this.pluginConfig = config as UCloudConnectionConfiguration
    }

    override fun PluginContext.initiateConnection(username: String): ConnectionResponse {
        try {
            val subject = UCloudSubject(username)
            val result = onConnectionComplete.invoke(pluginConfig.extensions.onConnectionComplete, subject)

            UserMapping.insertMapping(username, result.uid, this, mappingExpiration())

            IntegrationControl.approveConnection.callBlocking(
                IntegrationControlApproveConnectionRequest(username),
                rpcClient
            ).orThrow()

            return ConnectionResponse.Redirect(pluginConfig.redirectTo)
        } catch (ex: Throwable) {
            ex.printStackTrace()
            throw ex
        }
    }

    private companion object Extensions {
        val onConnectionComplete = extension<UCloudSubject, UidAndGid>()
    }

    @Serializable
    data class UCloudSubject(
        val username: String,
    )
}

