package dk.sdu.cloud.plugins

import kotlinx.serialization.*
import kotlinx.serialization.json.JsonObject

@Serializable
data class UidAndGid(val uid: Int, val gid: Int)

interface IdentityMapperPlugin : Plugin<JsonObject> {
    fun PluginContext.mapLocalIdentityToUidAndGid(localIdentity: String): UidAndGid
    fun PluginContext.mapUidToLocalIdentity(uid: Int): String
}
