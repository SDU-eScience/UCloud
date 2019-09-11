package dk.sdu.cloud.app.orchestrator.api

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NameAndVersionImpl
import dk.sdu.cloud.app.store.api.SimpleDuration

data class StartJobRequest(
    @JsonDeserialize(`as` = NameAndVersionImpl::class)
    val application: NameAndVersion,
    val name: String? = null,
    val parameters: Map<String, Any>,
    val numberOfNodes: Int? = null,
    val tasksPerNode: Int? = null,
    val maxTime: SimpleDuration? = null,
    val backend: String? = null,
    val archiveInCollection: String? = null,
    val mounts: List<Any> = emptyList(),
    val sharedFileSystemMounts: List<SharedFileSystemMountDescription> = emptyList(),
    val peers: List<ApplicationPeer> = emptyList(),
    val reservation: String? = null
)
