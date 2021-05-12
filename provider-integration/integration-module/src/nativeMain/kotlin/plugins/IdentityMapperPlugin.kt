package dk.sdu.cloud.plugins

data class UidAndGid(val uid: Int, val gid: Int)

interface IdentityMapperPlugin : Plugin {
    fun PluginContext.mapLocalIdentityToUidAndGid(localIdentity: String): UidAndGid
}
