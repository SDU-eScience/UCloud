package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.parents
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.unwrap

class ACLService<Ctx : FSUserContext>(private val fs: LowLevelFileSystemInterface<Ctx>) {
    fun grantRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        recursive: Boolean = true
    ) {
        val parents: List<String> = run {
            if (path == "/") throw FSException.BadRequest("Cannot grant rights on root")
            val parents = path.parents()

            parents.filter { it != "/" && it != "/home/" }
        }

        // Execute rights are required on all parent directories (otherwise we cannot perform the
        // traversal to the share)
        parents.forEach {
            fs.createACLEntry(ctx, it, entity, setOf(AccessRight.EXECUTE)).unwrap()
        }

        // Add to both the default and the actual list. This needs to be recursively applied
        fs.createACLEntry(ctx, path, entity, rights, defaultList = true, recursive = recursive).setfaclUnwrap()
        fs.createACLEntry(ctx, path, entity, rights, defaultList = false, recursive = recursive).setfaclUnwrap()
    }

    fun revokeRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        recursive: Boolean = true
    ) {
        fs.removeACLEntry(ctx, path, entity, defaultList = true, recursive = recursive).setfaclUnwrap()
        fs.removeACLEntry(ctx, path, entity, defaultList = false, recursive = recursive).setfaclUnwrap()
    }

    private fun <T> FSResult<T>.setfaclUnwrap(): T {
        if (statusCode == 256) {
            throw FSException.NotFound()
        }

        return unwrap()
    }
}
