package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.file.orchestrator.rpc.FileCollectionController
import dk.sdu.cloud.file.orchestrator.rpc.FileController
import dk.sdu.cloud.file.orchestrator.rpc.FileMetadataController
import dk.sdu.cloud.file.orchestrator.rpc.FileMetadataTemplateController
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        configureControllers(
            FileMetadataController(),
            FileController(),
            FileCollectionController(),
            FileMetadataTemplateController(),
        )

        startServices()
    }
}