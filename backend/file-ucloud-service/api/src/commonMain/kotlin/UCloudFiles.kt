package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider

object UCloudFiles : FilesProvider(UCLOUD_PROVIDER)
object UCloudFileCollections : FileCollectionsProvider(UCLOUD_PROVIDER)
