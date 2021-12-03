package dk.sdu.cloud.plugins

data class UidAndGid(val uid: Int, val gid: Int)

interface IdentityMapperPlugin : Plugin<Unit> {
    fun PluginContext.mapLocalIdentityToUidAndGid(localIdentity: String): UidAndGid
    fun PluginContext.mapUidToLocalIdentity(uid: Int): String
}
