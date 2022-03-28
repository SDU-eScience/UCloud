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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        // NOTE(Dan): The sync-mounter service is a special service which runs in tandem with Syncthing. It is
        // responsible for bind-mounting folders from our remote filesystem which are then consumed by Syncthing.
        //
        // To understand why this is needed, you must first understand that the security of UCloud/Stoage depends
        // heavily on never resolving a path which has symbolic links in it. The security model of UCloud/Storage
        // always implicitly grants access to a FileCollection. As a result, UCloud/Storage always assumes that if
        // a file is placed hiearchically under a FileCollection, and you have access to the collection, then you 
        // should also have access to the file. However, symbolic links makes this situation more complicated, as a
        // symbolic link could make a file appear to be in a FileCollection while pointing to a completely different
        // collection. UCloud/Storage resolves this by opening all files in a special way, which involves opening
        // each component in a path with `O_NOFOLLOW`. This way, we ensure that no single component of that path
        // is a link. Unfortuantely, Syncthing does not work this way. Thus we must trick Syncthing into never
        // attempting to follow any link. We do this by:
        //
        // 1. Opening the desired collection without following any link
        // 2. Use the /proc file-system to find the file-descriptor of the file we have just opened
        // 3. Bind-mount this file-descriptor file to a new location
        // 4. Ask syncthing to synchronize the bind-mounted folder
        //
        // This service receives instructions from UCloud/Storage about which folder to bind-mount and where to
        // bind-mount it. Authentication between this service and UCloud/Storage is done using a shared secret.
        if (syncMounterSharedSecret == null) {
            log.warn("Cannot start the sync-mounter without a shared secret")
            return
        }

        // Configure authentication
        val internalAuthenticator = RefreshingJWTAuthenticator(
            micro.client,
            JwtRefresherSharedSecret(syncMounterSharedSecret)
        )
        @Suppress("UNCHECKED_CAST")
        micro.providerTokenValidation = TokenValidationChain(listOf(
            InternalTokenValidationJWT.withSharedSecret(syncMounterSharedSecret)
        ))

        val internalClient = internalAuthenticator.authenticateClient(OutgoingHttpCall)

        // NOTE(Dan): The ready file is generally shared with syncthing. Syncthing will hold off on starting until
        // the ready file exists.
        val readyFile = File(joinPath(config.syncBaseMount, "ready"))

        val syncFolder = File(config.syncBaseMount).also { folder ->
            if (!folder.exists()) {
                if (!folder.mkdirs()) {
                    throw IllegalStateException("Unable to create folder: $folder. We cannot recover from this error.")
                }
            }
        }
        val mountService = MountService(config, ready)

        // This code attempts to unmount folders in the case of a graceful shutdown.
        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                readyFile.delete()

                mountService.unmount(
                    UnmountRequest(
                        syncFolder.listFiles().map { file ->
                            MountFolderId(file.name.toLong())
                        }
                    )
                )
            }
        })

        micro.backgroundScope.launch {
            log.info("Attempting to re-synchronize state with UCloud after a complete restart")
            readyFile.deleteOnExit()

            while (isActive) {
                try {
                    val folders = UCloudSyncFoldersBrowse(providerId).browse.call(
                        UCloudSyncFoldersBrowseRequest(config.deviceId),
                        internalClient
                    ).orThrow()

                    if (folders.isNotEmpty()) {
                        mountService.mount(
                            MountRequest(
                                folders.map { folder ->
                                    MountFolder(folder.id, folder.path)
                                }
                            )
                        )
                    }

                    break
                } catch (ex: Throwable) {
                    log.warn("Could not initialize Syncthing state. file-ucloud might not be ready yet.")
                    log.warn("The exception we encountered was:")
                    log.warn(ex.stackTraceToString())
                    log.warn("We will now attempt to retry the synchronization!")
                    delay(5000)
                }
            }

            readyFile.createNewFile()
            ready.set(true)
        }

        with(micro.server) {
            configureControllers(
                MountController(mountService)
            )
        }
        
        startServices()
    }
}

