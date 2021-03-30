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

        val distributedStateFactory = RedisDistributedStateFactory(micro)
        val metadataDao = MetadataDao()
        val projectCache = ProjectCache(authenticatedClient)
        val pathConverter = PathConverter(InternalFile(fsRootFile.absolutePath))
        val aclService = AclServiceImpl(authenticatedClient, projectCache, pathConverter, db, metadataDao)
        val fileQueries = FileQueries(aclService, pathConverter, distributedStateFactory)
        val trashService = TrashService(pathConverter)
        val taskSystem = TaskSystem(db).apply {
            install(CopyTask(aclService, pathConverter, micro.backgroundScope))
            install(DeleteTask(aclService, pathConverter, micro.backgroundScope))
            install(MoveTask(aclService, pathConverter, micro.backgroundScope))
            install(CreateFolderTask(aclService, pathConverter, micro.backgroundScope))
            install(TrashTask(aclService, pathConverter, micro.backgroundScope, trashService))
        }

        taskSystem.launchScheduler(micro.backgroundScope)

        configureControllers(
            FilesController(fileQueries, taskSystem),
            FileCollectionsController(),
        )

        startServices()
    }
}
