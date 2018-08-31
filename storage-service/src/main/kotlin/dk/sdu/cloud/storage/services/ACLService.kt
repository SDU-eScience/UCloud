package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.storage.util.parents

class ACLService<Ctx : FSUserContext>(private val fs: LowLevelFileSystemInterface<Ctx>) {
    fun grantRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>
    ) {
        val parents: List<String> = run {
            if (path == "/") throw ShareException.BadRequest("Cannot grant rights on root")
            val parents = path.parents()

            parents.filter { it != "/" && it != "/home/" }
        }

        // Execute rights are required on all parent directories (otherwise we cannot perform the
        // traversal to the share)
        parents.forEach {
            fs.createACLEntry(ctx, it, entity, setOf(AccessRight.EXECUTE))
        }

        // Add to both the default and the actual list. This needs to be recursively applied
        fs.createACLEntry(ctx, path, entity, rights, defaultList = true, recursive = true)
        fs.createACLEntry(ctx, path, entity, rights, defaultList = false, recursive = true)
    }

    fun revokeRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity
    ) {
        fs.removeACLEntry(ctx, path, entity, defaultList = true, recursive = true)
        fs.removeACLEntry(ctx, path, entity, defaultList = false, recursive = true)
    }
}