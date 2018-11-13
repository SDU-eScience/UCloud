package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.storage.util.parents
import dk.sdu.cloud.storage.util.unwrap

class ACLService<Ctx : FSUserContext>(private val fs: LowLevelFileSystemInterface<Ctx>) {
    fun grantRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        recursive: Boolean = true
    ) {
        val parents: List<String> = run {
            if (path == "/") throw ShareException.BadRequest("Cannot grant rights on root")
            val parents = path.parents()

            parents.filter { it != "/" && it != "/home/" }
        }

        // Execute rights are required on all parent directories (otherwise we cannot perform the
        // traversal to the share)
        parents.forEach {
            fs.createACLEntry(ctx, it, entity, setOf(AccessRight.EXECUTE)).unwrap()
        }

        // Add to both the default and the actual list. This needs to be recursively applied
        fs.createACLEntry(ctx, path, entity, rights, defaultList = true, recursive = recursive).unwrap()
        fs.createACLEntry(ctx, path, entity, rights, defaultList = false, recursive = recursive).unwrap()
    }

    fun revokeRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        recursive: Boolean = true
    ) {
        fs.removeACLEntry(ctx, path, entity, defaultList = true, recursive = recursive).unwrap()
        fs.removeACLEntry(ctx, path, entity, defaultList = false, recursive = recursive).unwrap()
    }
}
