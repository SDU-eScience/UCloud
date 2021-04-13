package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.orchestrator.rpc.FileCollectionController
import dk.sdu.cloud.file.orchestrator.rpc.FileController
import dk.sdu.cloud.file.orchestrator.rpc.FileMetadataController
import dk.sdu.cloud.file.orchestrator.rpc.FileMetadataTemplateController
import dk.sdu.cloud.file.orchestrator.service.*
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val providers = Providers(serviceClient)
        val providerSupport = ProviderSupport(providers, serviceClient)
        val projectCache = ProjectCache(serviceClient)
        val filesService = FilesService(providers, providerSupport, projectCache)
        val fileCollections = FileCollectionService(providers, providerSupport, projectCache)

        configureControllers(
            FileMetadataController(),
            FileController(filesService),
            FileCollectionController(fileCollections),
            FileMetadataTemplateController(),
        )

        startServices()
    }
}