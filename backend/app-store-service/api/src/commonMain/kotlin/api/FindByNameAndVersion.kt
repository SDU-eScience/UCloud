package dk.sdu.cloud.app.store.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FindByNameAndVersion(val name: String, val version: String)
