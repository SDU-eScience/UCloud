package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.accounting.api.ErrorCode
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.components
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.bindStringNullable
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession

class LimitChecker(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val pathConverter: PathConverter,
) {
    private data class LimitKey(val username: String?, val projectId: String?, val category: String)

    private val isCollectionLocked = SimpleCache<LimitKey, Boolean>(
        maxAge = 60_00 * 5L,
        lookup = { key ->
            var isLocked = false
            db.withSession { session ->
                session.prepareStatement(
                    //language=postgresql
                    """
                        select true
                        from ucloud_storage_quota_locked
                        where
                            username is not distinct from :username::text and
                            project_id is not distinct from :project_id::text and
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

    private val collectionCache = SimpleCache<String, FileCollection> { collectionId ->
        FileCollectionsControl.retrieve.call(
            ResourceRetrieveRequest(
                FileCollectionIncludeFlags(),
                collectionId
            ),
            serviceClient
        ).orThrow()
    }

    suspend fun checkLimit(collection: String) {
        // NOTE(Dan): This resolves any share we might have been passed
        val normalizedCollection = pathConverter.internalToUCloud(
            pathConverter.ucloudToInternal(UCloudFile.createFromPreNormalizedString("/${collection}"))
        ).components()[0]

        val cachedCollection = collectionCache.get(normalizedCollection)
            ?: throw RPCException("Unknown drive, are you sure it exists?", HttpStatusCode.NotFound)

        val key = LimitKey(
            cachedCollection.owner.createdBy,
            cachedCollection.owner.project,
            cachedCollection.specification.product.category
        )

        if (isCollectionLocked.get(key) == true) {
            log.info("Collection is locked: $key")
            throw RPCException(
                "Quota has been exceeded. Delete some files and try again later.",
                HttpStatusCode.PaymentRequired,
                ErrorCode.EXCEEDED_STORAGE_QUOTA.toString()
            )
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

suspend inline fun LimitChecker.checkLimit(collection: FileCollection) {
    checkLimit(collection.id)
}
