package dk.sdu.cloud.app.store.api

import dk.sdu.cloud.calls.UCloudApiDoc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiDoc("A request type to find a resource by name and version")
data class FindByNameAndVersion(val name: String, val version: String)
