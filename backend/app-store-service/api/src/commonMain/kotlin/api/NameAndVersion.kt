package dk.sdu.cloud.app.store.api

import kotlinx.serialization.Serializable

interface NameAndVersion {
    val name: String
    val version: String
}

@Serializable
data class NameAndVersionImpl(override val name: String, override val version: String) : NameAndVersion {
    override fun toString() = "$name@$version"
}

@Suppress("FunctionNaming")
fun NameAndVersion(name: String, version: String): NameAndVersion = NameAndVersionImpl(name, version)
