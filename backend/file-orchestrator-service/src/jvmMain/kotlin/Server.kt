package dk.sdu.cloud.file.orchestrator

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.orchestrator.rpc.*
import dk.sdu.cloud.file.orchestrator.service.*
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val providers = Providers(serviceClient)
        val providerSupport = ProviderSupport(providers, serviceClient)
        val projectCache = ProjectCache(serviceClient)
        val metadataTemplates = MetadataTemplates(db, projectCache)
        val metadataService = MetadataService(db, projectCache, metadataTemplates)
        val filesService = FilesService(providers, providerSupport, projectCache, metadataService)
        val fileCollections = FileCollectionService(providers, providerSupport, projectCache, db)
        val shares = ShareService(db, serviceClient, micro.backgroundScope)

        configureControllers(
            FileMetadataController(metadataService),
            FileController(filesService),
            FileCollectionController(fileCollections),
            FileMetadataTemplateController(metadataTemplates),
            ShareController(shares)
        )

        startServices()
    }
}
