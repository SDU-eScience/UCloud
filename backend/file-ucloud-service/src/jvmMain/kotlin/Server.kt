package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.file.ucloud.rpc.FileCollectionsController
import dk.sdu.cloud.file.ucloud.rpc.FilesController
import dk.sdu.cloud.file.ucloud.rpc.ShareController
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.tasks.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class Server(
    override val micro: Micro,
    private val configuration: Configuration,
    private val cephConfig: CephConfiguration,
) : CommonServer {
    override val log = logger()
    private val onKtorReadyCallbacks = ArrayList<suspend () -> Unit>()

    @OptIn(ExperimentalStdlibApi::class)
    override fun start() {
        /*
        NOTE(Dan): This service has at this point gotten fairly complex in terms of functionality. The start function,
        as in any other service, is responsible for creating, initializing and starting the internal services. The
        function goes through a number of steps, make sure you add your code in the relevant section.

        1. Provider configuration
        2. Core infrastructure (e.g. databases)
        3. Mandatory features
        4. Optional features
        5. Controllers
         */

        // NOTE(Dan): Optional features can choose to add to this list, they will be configured as part of
        // `configureControllers()`.
        val controllers = ArrayList<Controller>()

        // 1. Provider configuration
        // ===========================================================================================================
        val (refreshToken, validation) =
            if (configuration.providerRefreshToken == null || configuration.ucloudCertificate == null) {
                throw IllegalStateException("Missing configuration at files.ucloud.providerRefreshToken")
            } else {
                Pair(
                    configuration.providerRefreshToken,
                    InternalTokenValidationJWT.withPublicCertificate(configuration.ucloudCertificate)
                )
            }

        val authenticator =
            RefreshingJWTAuthenticator(micro.client, JwtRefresher.Provider(refreshToken, OutgoingHttpCall))

        micro.providerTokenValidation = validation as TokenValidation<Any>

        // 2. Core infrastructure (e.g. databases)
        // ===========================================================================================================
        val authenticatedClient = authenticator.authenticateClient(OutgoingHttpCall)
        val db = AsyncDBSessionFactory(micro)
        val distributedStateFactory = DistributedStateFactory(micro)
        val scriptManager = micro.feature(ScriptManager)

        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        // 3. Mandatory features
        // ===========================================================================================================
        val pathConverter = PathConverter(
            configuration.providerId,
            configuration.productCategory,
            InternalFile(fsRootFile.absolutePath),
            authenticatedClient
        )
        val nativeFs = NativeFS(pathConverter, micro)
        val cephStats = CephFsFastDirectoryStats(nativeFs)

        val limitChecker = LimitChecker(db, pathConverter)
        val trashService = TrashService(pathConverter)
        val fileQueries = FileQueries(pathConverter, distributedStateFactory, nativeFs, trashService, cephStats)
        val chunkedUploadService = ChunkedUploadService(db, pathConverter, nativeFs)
        val downloadService = DownloadService(configuration.providerId, db, pathConverter, nativeFs)
        val memberFiles = MemberFiles(nativeFs, pathConverter, authenticatedClient)

        val shareService = ShareService(nativeFs, pathConverter, authenticatedClient)
        val taskSystem = TaskSystem(
            db,
            pathConverter,
            nativeFs,
            micro.backgroundScope,
            authenticatedClient,
            micro.feature(DebugSystem)
        ).apply {
            install(CopyTask())
            install(DeleteTask())
            install(MoveTask())
            install(CreateFolderTask())
            install(TrashTask(memberFiles, trashService))
            install(EmptyTrashTask())

            launchScheduler(micro.backgroundScope)
        }
        val fileCollectionService = FileCollectionsService(
            pathConverter,
            db,
            taskSystem,
            nativeFs,
            memberFiles
        )

        // 4a. Optional indexing feature
        // ===========================================================================================================
        var elasticQueryService: ElasticQueryService? = null
        if (configuration.indexing.enabled && micro.featureOrNull(ElasticFeature) != null) {
            FilesIndex.create(micro.elasticHighLevelClient, numberOfShards = 2, numberOfReplicas = 5)
            elasticQueryService = ElasticQueryService(
                micro.elasticHighLevelClient,
                nativeFs,
                pathConverter
            )

            scriptManager.register(
                Script(
                    ScriptMetadata(
                        "ucloud-storage-index",
                        "UCloud/Storage: Indexing",
                        WhenToStart.Periodically(1000L * 60L * 60L * 96)
                    ),
                    script = {
                        FileScanner(
                            micro.elasticHighLevelClient,
                            authenticatedClient,
                            db,
                            nativeFs,
                            pathConverter,
                            cephStats,
                            elasticQueryService
                        ).runScan()
                    }
                )
            )
        }

        // 4c. Optional accounting feature
        // ===========================================================================================================
        if (configuration.accounting.enabled) {
            log.debug("Accounting is enabled!")
            val usageScan = UsageScan(pathConverter, nativeFs, cephStats, authenticatedClient, db)
            scriptManager.register(
                Script(
                    ScriptMetadata(
                        "ucloud-scan",
                        "UCloud/Storage: Accounting",
                        WhenToStart.Periodically(1000L * 60L * 60L * 3)
                    ),
                    script = {
                        usageScan.startScan()
                    }
                )
            )
        }

        // 5. Controllers
        // ===========================================================================================================
        configureControllers(
            *buildList {
                addAll(controllers)

                add(
                    FilesController(
                        configuration.providerId,
                        fileQueries,
                        taskSystem,
                        chunkedUploadService,
                        downloadService,
                        limitChecker,
                        elasticQueryService,
                        memberFiles
                    )
                )
                add(FileCollectionsController(configuration.providerId, fileCollectionService))
                add(ShareController(configuration.providerId, pathConverter, shareService))
            }.toTypedArray()
        )

        startServices()
    }

    override fun onKtorReady() {
        runBlocking {
            for (callback in onKtorReadyCallbacks) callback()
        }
    }
}
