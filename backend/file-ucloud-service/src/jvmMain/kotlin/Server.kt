package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.ucloud.rpc.FileCollectionsController
import dk.sdu.cloud.file.ucloud.rpc.FilesController
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.acl.AclServiceImpl
import dk.sdu.cloud.file.ucloud.services.acl.MetadataDao
import dk.sdu.cloud.file.ucloud.services.tasks.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import java.io.File
import java.util.*

class Server(
    override val micro: Micro,
    private val configuration: Configuration,
    private val cephConfig: CephConfiguration,
) : CommonServer {
    override val log = logger()

    override fun start() {
        val (refreshToken, validation) =
            if (configuration.providerRefreshToken == null || configuration.ucloudCertificate == null) {
                throw IllegalStateException("Missing configuration at files.ucloud.providerRefreshToken")
            } else {
                Pair(
                    configuration.providerRefreshToken,
                    InternalTokenValidationJWT.withPublicCertificate(configuration.ucloudCertificate)
                )
            }

        val authenticator = RefreshingJWTAuthenticator(micro.client, JwtRefresher.Provider(refreshToken))
        @Suppress("UNCHECKED_CAST")
        micro.providerTokenValidation = validation as TokenValidation<Any>
        val authenticatedClient = authenticator.authenticateClient(OutgoingHttpCall)
        val db = AsyncDBSessionFactory(micro.databaseConfig)

        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        val pathConverter = PathConverter(InternalFile(fsRootFile.absolutePath))
        val nativeFs = NativeFS(pathConverter)
        val distributedStateFactory = RedisDistributedStateFactory(micro)
        val metadataDao = MetadataDao()
        val projectCache = ProjectCache(authenticatedClient)
        val aclService = AclServiceImpl(authenticatedClient, projectCache, pathConverter, db, metadataDao)
        val trashService = TrashService(pathConverter)
        val fileQueries = FileQueries(aclService, pathConverter, distributedStateFactory, nativeFs, trashService)
        val chunkedUploadService = ChunkedUploadService(db, aclService, pathConverter, nativeFs)
        val taskSystem = TaskSystem(db, aclService, pathConverter, nativeFs, micro.backgroundScope).apply {
            install(CopyTask())
            install(DeleteTask())
            install(MoveTask())
            install(CreateFolderTask())
            install(TrashTask(trashService))
        }
        val fileCollectionService = FileCollectionsService(
            aclService,
            pathConverter,
            db,
            projectCache,
            taskSystem,
            nativeFs
        )

        taskSystem.launchScheduler(micro.backgroundScope)

        configureControllers(
            FilesController(fileQueries, taskSystem, chunkedUploadService, aclService),
            FileCollectionsController(fileCollectionService),
        )

        startServices()
    }
}
