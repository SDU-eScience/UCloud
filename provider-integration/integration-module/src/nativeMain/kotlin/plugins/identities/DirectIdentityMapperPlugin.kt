package dk.sdu.cloud.plugins.identities

import dk.sdu.cloud.plugins.IdentityMapperPlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.UidAndGid

class DirectIdentityMapperPlugin : IdentityMapperPlugin {
    override fun PluginContext.mapLocalIdentityToUidAndGid(localIdentity: String): UidAndGid {
        val id = localIdentity.toIntOrNull(10) ?: error("Invalid local identity: $localIdentity")
        return UidAndGid(id, id)
    }
}
