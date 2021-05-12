package dk.sdu.cloud.plugins

class DirectIdentityMapperPlugin : IdentityMapperPlugin {
    override fun PluginContext.mapLocalIdentityToUidAndGid(localIdentity: String): UidAndGid {
        val id = localIdentity.toIntOrNull(10) ?: error("Invalid local identity: $localIdentity")
        return UidAndGid(id, id)
    }
}
