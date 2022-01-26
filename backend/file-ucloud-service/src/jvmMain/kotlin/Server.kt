package dk.sdu.cloud.file.ucloud

import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.file.ucloud.rpc.FileCollectionsController
import dk.sdu.cloud.file.ucloud.rpc.FilesController
import dk.sdu.cloud.file.ucloud.rpc.ShareController
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.file.ucloud.services.tasks.*
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.Roles
import java.io.File
import kotlin.system.exitProcess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.elasticsearch.client.RestHighLevelClient
import dk.sdu.cloud.file.ucloud.services.FileScanner

// NOTE(Dan): This is only used in development mode
object Scans : CallDescriptionContainer("file.ucloud.scans") {
    const val baseContext = "/api/file/ucloud/scans"

    val start = call<Unit, Unit, CommonErrorMessage>("start") {
        httpUpdate(baseContext, "start", roles = Roles.PUBLIC)
    }
}

class Server(
    override val micro: Micro,
    private val configuration: Configuration,
    private val cephConfig: CephConfiguration,
) : CommonServer {
    override val log = logger()
    private lateinit var elastic: RestHighLevelClient

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
        val db = AsyncDBSessionFactory(micro)

        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        val pathConverter = PathConverter(InternalFile(fsRootFile.absolutePath), authenticatedClient)
        val nativeFs = NativeFS(pathConverter, micro)
        val cephStats = CephFsFastDirectoryStats(nativeFs)

        elastic = micro.elasticHighLevelClient
        val limitChecker = LimitChecker(db, pathConverter)
        val usageScan = UsageScan(pathConverter, nativeFs, cephStats, authenticatedClient, db)

        val scriptManager = micro.feature(ScriptManager)

        val distributedStateFactory = RedisDistributedStateFactory(micro)
        val trashService = TrashService(pathConverter)
        val fileQueries = FileQueries(pathConverter, distributedStateFactory, nativeFs, trashService, cephStats)
        val chunkedUploadService = ChunkedUploadService(db, pathConverter, nativeFs)
        val downloadService = DownloadService(db, pathConverter, nativeFs)
        val memberFiles = MemberFiles(nativeFs, pathConverter, authenticatedClient)
        val shareService = ShareService(nativeFs, pathConverter, authenticatedClient)
        val taskSystem = TaskSystem(db, pathConverter, nativeFs, micro.backgroundScope, authenticatedClient,
            micro.feature(DebugSystem)).apply {
            install(CopyTask())
            install(DeleteTask())
            install(MoveTask())
            install(CreateFolderTask())
            install(TrashTask(memberFiles, trashService))
            install(EmptyTrashTask())
        }
        val fileCollectionService = FileCollectionsService(
            pathConverter,
            db,
            taskSystem,
            nativeFs,
            memberFiles
        )

        FilesIndex.create(micro.elasticHighLevelClient, numberOfShards = 2, numberOfReplicas = 5)

        val elasticQueryService = ElasticQueryService(
            micro.elasticHighLevelClient,
            nativeFs,
            pathConverter
        )

        taskSystem.launchScheduler(micro.backgroundScope)

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

        configureControllers(
            FilesController(fileQueries, taskSystem, chunkedUploadService, downloadService, limitChecker, elasticQueryService, memberFiles),
            FileCollectionsController(fileCollectionService),
            ShareController(shareService),
            object : Controller {
                override fun configure(rpcServer: RpcServer) {
                    if (micro.developmentModeEnabled) {
                        rpcServer.implement(Scans.start) {
                            GlobalScope.launch {
                                usageScan.startScan()
                            }
                            ok(Unit)
                        }
                    }
                }
            }
        )

        startServices()
    }
}

