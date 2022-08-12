package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.accounting.api.ErrorCode
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession

class LimitChecker(
    private val db: DBContext,
    private val pathConverter: PathConverter,
) {
    private data class LimitKey(val username: String?, val projectId: String?, val category: String)

    private val isCollectionLocked = SimpleCache<LimitKey, Boolean>(
        maxAge = 60_00 * 5L,
        lookup = { key ->
            var isLocked = false
            db.withSession { session ->
                session.prepareStatement(
                    """
                        select true
                        from file_ucloud.quota_locked
                        where
                            username is not distinct from :username and
                            project_id is not distinct from :project_id and
                            category is not distinct from :category
                    """,
                ).useAndInvoke(
                    prepare = {
                        bindStringNullable("username", key.username.takeIf { key.projectId == null })
                        bindStringNullable("project_id", key.projectId)
                        bindString("category", key.category)
                    },
                    readRow = { isLocked = true }
                )
            }
            isLocked
        }
    )
    suspend fun checkLimit(collection: String) {
        val cachedCollection = pathConverter.collectionCache.get(collection)
            ?: throw RPCException("Unknown drive, are you sure it exists?", HttpStatusCode.NotFound)

        val key = LimitKey(
            cachedCollection.owner.createdBy,
            cachedCollection.owner.project,
            cachedCollection.specification.product.category
        )

        if (isCollectionLocked.get(key) == true) {
            throw RPCException(
                "Quota has been exceeded. Delete some files and try again later.",
                HttpStatusCode.PaymentRequired,
                ErrorCode.EXCEEDED_STORAGE_QUOTA.toString()
            )
        }
    }
}

suspend inline fun LimitChecker.checkLimit(collection: FileCollection) {
    checkLimit(collection.id)
}
