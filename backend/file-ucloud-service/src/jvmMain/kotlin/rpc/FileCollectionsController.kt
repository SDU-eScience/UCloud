package dk.sdu.cloud.file.ucloud.rpc

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.UCloudFileCollections
import dk.sdu.cloud.file.ucloud.api.UCloudFiles
import dk.sdu.cloud.service.Controller

class FileCollectionsController : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(UCloudFileCollections.retrieveManifest) {
            // TODO We probably need to split the home and project partition into two different products
            ok(
                FileCollectionsProviderRetrieveManifestResponse(
                    listOf(
                        FSSupport(
                            ProductReference("u1-cephfs", "u1-cephfs", UCLOUD_PROVIDER),

                            FSProductStatsSupport(
                                sizeInBytes = true,
                                sizeIncludingChildrenInBytes = false,
                                modifiedAt = true,
                                createdAt = false,
                                accessedAt = true,
                                unixPermissions = true,
                                unixOwner = true,
                                unixGroup = true
                            ),

                            FSCollectionSupport(
                                aclSupported = true,
                                aclModifiable = true,
                                usersCanCreate = true,
                                usersCanDelete = true,
                                usersCanRename = true,
                                searchSupported = true
                            ),

                            FSFileSupport(
                                aclSupported = true,
                                aclModifiable = true,
                                trashSupported = true,
                                isReadOnly = false
                            )
                        )
                    )
                )
            )
        }

        return@with
    }
}