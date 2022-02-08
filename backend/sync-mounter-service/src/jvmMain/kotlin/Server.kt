package dk.sdu.cloud.sync.mounter 

import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.ucloud.api.UCloudSyncFoldersBrowse
import dk.sdu.cloud.file.ucloud.api.UCloudSyncFoldersBrowseRequest
import dk.sdu.cloud.file.ucloud.services.InternalFile
import dk.sdu.cloud.file.ucloud.services.JwtRefresherSharedSecret
import dk.sdu.cloud.file.ucloud.services.PathConverter
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.micro.providerTokenValidation
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.*
import dk.sdu.cloud.sync.mounter.api.*
import dk.sdu.cloud.sync.mounter.http.MountController
import dk.sdu.cloud.sync.mounter.services.MountService
import kotlinx.coroutines.runBlocking
import dk.sdu.cloud.service.TokenValidationChain
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class Server(
    override val micro: Micro,
    private val config: SyncMounterConfiguration,
    private val syncMounterSharedSecret: String?,
    private val providerId: String,
) : CommonServer {
    override val log = logger()
    private val ready: AtomicBoolean = AtomicBoolean(false)
    
    override fun start() {
        if (syncMounterSharedSecret == null) {
            log.warn("Cannot start the sync-mounter without a shared secret")
            return
        }
        val mountService = MountService(config, ready)
        val internalAuthenticator = RefreshingJWTAuthenticator(
            micro.client,
            JwtRefresherSharedSecret(syncMounterSharedSecret)
        )
        @Suppress("UNCHECKED_CAST")
        micro.providerTokenValidation = TokenValidationChain(listOf(
            InternalTokenValidationJWT.withSharedSecret(syncMounterSharedSecret)
        ))

        val internalClient = internalAuthenticator.authenticateClient(OutgoingHttpCall)

        with(micro.server) {
            configureControllers(
                MountController(mountService)
            )
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                File(joinPath(config.syncBaseMount, "ready")).delete()

                val syncDir = File(joinPath(config.syncBaseMount))

                mountService.unmount(
                    UnmountRequest(
                        syncDir.listFiles().map { file ->
                            MountFolderId(file.name.toLong())
                        }
                    )
                )
            }
        })
        
        startServices()
    }

    override fun onKtorReady() {
        if (syncMounterSharedSecret == null) {
            return
        }

        runBlocking {
            val readyFile = File(joinPath(config.syncBaseMount, "ready"))
            readyFile.deleteOnExit()

            try {
                val syncFolder = File(config.syncBaseMount)
                if (!syncFolder.exists()) {
                    syncFolder.mkdir()
                }

                val internalAuthenticator = RefreshingJWTAuthenticator(
                    micro.client,
                    JwtRefresherSharedSecret(syncMounterSharedSecret)
                )
                @Suppress("UNCHECKED_CAST")
                micro.providerTokenValidation = TokenValidationChain(listOf(
                    InternalTokenValidationJWT.withSharedSecret(syncMounterSharedSecret)
                ))

                val client = internalAuthenticator.authenticateClient(OutgoingHttpCall)

                val mountService = MountService(config, ready)

                val folders = UCloudSyncFoldersBrowse(providerId).browse.call(
                    UCloudSyncFoldersBrowseRequest(config.deviceId),
                    client
                ).orThrow()

                if (folders.length > 0) {
                    mountService.mount(
                        MountRequest(
                            folders.map { folder ->
                                MountFolder(folder.id, folder.path)
                            }
                        )
                    )
                }
            } catch (ex: Throwable) {
                log.warn("Caught exception while initializing mounter (is file-ucloud down?)")
                log.warn(ex.stackTraceToString())
            }

            readyFile.createNewFile()
            ready.set(true)
        }
    }
}
