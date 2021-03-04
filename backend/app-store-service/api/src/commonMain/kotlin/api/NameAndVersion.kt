package dk.sdu.cloud.app.store.api

import kotlinx.serialization.Serializable

interface WithNameAndVersion {
    val name: String
    val version: String
}

@Serializable
data class NameAndVersion(override val name: String, override val version: String) : WithNameAndVersion {
    override fun toString() = "$name@$version"
}
