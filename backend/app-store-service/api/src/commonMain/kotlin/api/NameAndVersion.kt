package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.UCloudApiOwnedBy
import kotlinx.serialization.Serializable

interface WithNameAndVersion {
    val name: String
    val version: String
}

@Serializable
@UCloudApiOwnedBy(ToolStore::class)
data class NameAndVersion(override val name: String, override val version: String) : WithNameAndVersion {
    override fun toString() = "$name@$version"
}
