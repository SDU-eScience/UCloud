package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.calls.TSNamespace
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.api.FilesProvider

@TSNamespace("file.ucloud.files")
object UCloudFiles : FilesProvider(UCLOUD_PROVIDER)
@TSNamespace("file.ucloud.filecollections")
object UCloudFileCollections : FileCollectionsProvider(UCLOUD_PROVIDER)
