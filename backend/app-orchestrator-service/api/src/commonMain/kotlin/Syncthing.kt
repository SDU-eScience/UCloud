package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.calls.typeOfIfPossible
import dk.sdu.cloud.provider.api.Permission
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer

@Serializable
data class SyncthingConfig(
    val folders: List<Folder>,
    val devices: List<Device>,
    val orchestratorInfo: OrchestratorInfo? = null,
) {
    @Serializable
    data class Folder(
        val ucloudPath: String,
        val path: String = "",
        val id: String = "",
    )

    @Serializable
    data class Device(
        val deviceId: String,
        val label: String,
    )

    @Serializable
    data class OrchestratorInfo(
        val folderPathToPermission: Map<String, List<Permission>>,
    )
}

object Syncthing : IntegratedApplicationApi<SyncthingConfig>("syncthing") {
    override val configType = typeOfIfPossible<SyncthingConfig>()
    override val configSerializer = SyncthingConfig.serializer()
}

class SyncthingProvider(provider: String) : IntegratedApplicationProviderApi<SyncthingConfig>("syncthing", provider) {
    override val configType = typeOfIfPossible<SyncthingConfig>()
    override val configSerializer = SyncthingConfig.serializer()
}

