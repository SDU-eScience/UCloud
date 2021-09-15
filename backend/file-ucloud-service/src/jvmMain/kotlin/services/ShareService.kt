package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.accounting.api.providers.ProviderRegisteredResource
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.Share
import dk.sdu.cloud.file.orchestrator.api.SharesControl
import dk.sdu.cloud.file.orchestrator.api.fileName
import dk.sdu.cloud.provider.api.ResourceUpdateAndId
import dk.sdu.cloud.service.Time
import io.ktor.http.*

class ShareService(
    private val fs: NativeFS,
    private val pathConverter: PathConverter,
    private val serviceClient: AuthenticatedClient
) {
    suspend fun create(request: BulkRequest<Share>): BulkResponse<FindByStringId?> {
        request.items.forEach { path ->
            val file = pathConverter.ucloudToInternal(UCloudFile.create(path.specification.sourceFilePath))
            try {
                val stat = fs.stat(file)
                if (stat.fileType != FileType.DIRECTORY) {
                    throw RPCException("'${file.fileName()}' is not a directory", HttpStatusCode.BadRequest)
                }
            } catch (ex: FSException.NotFound) {
                throw RPCException("'${file.fileName()}' no longer exists", HttpStatusCode.BadRequest)
            }
        }

        val ids = FileCollectionsControl.register.call(
            BulkRequest(
                request.items.map { share ->
                    ProviderRegisteredResource(
                        FileCollection.Spec(
                            share.specification.sourceFilePath.fileName(),
                            PathConverter.PRODUCT_SHARE_REFERENCE
                        ),
                        PathConverter.COLLECTION_SHARE_PREFIX + share.id,
                        createdBy = "_ucloud",
                        project = null,
                    )
                }
            ),
            serviceClient
        ).orThrow()

        SharesControl.update.call(
            BulkRequest(
                request.items.zip(ids.responses).map { (share, collection) ->
                    ResourceUpdateAndId(
                        share.id,
                        Share.Update(
                            Share.State.PENDING,
                            "/${collection.id}",
                            Time.now(),
                            null
                        )
                    )
                }
            ),
            serviceClient
        ).orThrow()

        return BulkResponse(request.items.map { null })
    }
}
