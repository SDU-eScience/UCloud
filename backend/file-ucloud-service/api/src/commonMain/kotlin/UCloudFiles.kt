package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.file.orchestrator.FileCollectionsProvider
import dk.sdu.cloud.file.orchestrator.FilesProvider

class UCloudFiles : FilesProvider(UCLOUD_PROVIDER)
class UCloudFileCollections : FileCollectionsProvider(UCLOUD_PROVIDER)
