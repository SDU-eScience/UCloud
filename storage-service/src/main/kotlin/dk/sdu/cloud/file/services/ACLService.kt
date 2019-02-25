package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.parents
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.unwrap

class ACLService<Ctx : FSUserContext>(private val fs: LowLevelFileSystemInterface<Ctx>) {
    suspend fun grantRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        recursive: Boolean = true
    ) {
        // Add to both the default and the actual list. This needs to be recursively applied
        // We need to apply this process to both the owner and the entity.
        fs.createACLEntry(ctx, path, FSACLEntity.User(ctx.user), rights, defaultList = true, recursive = recursive).setfaclUnwrap()
        fs.createACLEntry(ctx, path, FSACLEntity.User(ctx.user), rights, defaultList = false, recursive = recursive).setfaclUnwrap()

        fs.createACLEntry(ctx, path, entity, rights, defaultList = true, recursive = recursive).setfaclUnwrap()
        fs.createACLEntry(ctx, path, entity, rights, defaultList = false, recursive = recursive).setfaclUnwrap()
    }

    suspend fun revokeRights(
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
