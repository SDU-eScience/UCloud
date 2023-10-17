package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductCategoryIdV2
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.ActivitySystem
import dk.sdu.cloud.utils.forEachGraal
import dk.sdu.cloud.utils.reportConcurrentUseStorage
import dk.sdu.cloud.utils.whileGraal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.CoroutineContext

class UsageScan(
    private val pluginName: String,
    private val pathConverter: PathConverter,
    private val fastDirectoryStats: FastDirectoryStats,
) {
    private data class ScanTask(val driveInfo: DriveAndSystem, val reportOnly: Boolean = false)
    private val scanQueue = Channel<ScanTask>(Channel.BUFFERED)

    fun init() {
        ProcessingScope.launch {
            val taskName = "storage-scan-ucloud"
            whileGraal({ isActive }) {
                val start = Time.now()
                Prometheus.countBackgroundTask(taskName)
                try {
                    var next: String? = null
                    var innerActive = true
                    whileGraal({ innerActive }) {
                        val page = pathConverter.locator.enumerateDrives(next = next)
                        val driveIds = page.items.map { it.drive.ucloudId }
                        val lastScans = HashMap<Long, Long>()
                        val now = Time.now()
                        usageScanFetchDriveIdsForScanGraal(driveIds, lastScans, now)

                        page.items.forEachGraal { item ->
                            val timeSinceLastScan = now - (lastScans[item.drive.ucloudId] ?: 0L)
                            val metadata = pathConverter.locator.fetchMetadataForDrive(item.drive.ucloudId) ?: return@forEachGraal
                            val timeSinceOwnerActive = now - ActivitySystem.queryLastActiveWalletOwner(metadata.workspace)

                            if (timeSinceLastScan >= 1000L * 60 * 60 * 12) {
                                scanQueue.send(ScanTask(item, reportOnly = timeSinceOwnerActive > ONE_MONTH_MILLIS))
                            }
                        }

                        val nextToken = page.next
                        if (nextToken == null) {
                            innerActive = false
                        } else {
                            next = nextToken
                        }
                    }
                } catch (ex: Throwable) {
                    log.warn("Caught exception while scanning usage for $pluginName: ${ex.toReadableStacktrace()}")
                } finally {
                    val duration = Time.now() - start
                    Prometheus.measureBackgroundDuration(taskName, duration)
                    delay(60_000 - duration)
                }
            }
        }

        repeat(Runtime.getRuntime().availableProcessors()) { taskId ->
            ScanningScope.launch {
                var loopActive = true
                whileGraal({ isActive && loopActive }) {
                    try {
                        val task = scanQueue.receiveCatching().getOrNull() ?: run {
                            loopActive = false
                            return@whileGraal
                        }
                        val driveInfo = task.driveInfo
                        val driveRoot = driveInfo.driveRoot ?: return@whileGraal
                        if (driveInfo.inMaintenanceMode) return@whileGraal
                        // TODO This blocks the thread while we are collecting the size.
                        val size = fastDirectoryStats.getRecursiveSize(driveRoot, allowSlowPath = true) ?: return@whileGraal

                        pathConverter.locator.updateDriveSize(driveInfo.drive.ucloudId, size)

                        val ucloudMetadata = pathConverter.locator.fetchMetadataForDrive(driveInfo.drive.ucloudId)
                            ?: return@whileGraal

                        val allDrives = pathConverter.locator.listDrivesByWorkspace(ucloudMetadata.workspace)
                            .filter {
                                it.product?.category == ucloudMetadata.product.category &&
                                        it.drive.type != UCloudDrive.Type.SHARE
                            }

                        val combinedSizeInBytes = allDrives.sumOf { it.estimatedSizeInBytes }

                        val success = reportConcurrentUseStorage(
                            ucloudMetadata.workspace,
                            ProductCategoryIdV2(ucloudMetadata.product.category, ucloudMetadata.product.provider),
                            combinedSizeInBytes,
                        )

                        dbConnection.withSession { session ->
                            if (!success) {
                                session.prepareStatement(
                                    """
                                        insert into ucloud_storage_quota_locked (scan_id, category, username, project_id)
                                        values ('unused', :category, :username, :project)
                                    """
                                ).useAndInvokeAndDiscard(
                                    prepare = {
                                        bindString("category", ucloudMetadata.product.category)
                                        bindStringNullable("username", (ucloudMetadata.workspace as? WalletOwner.User)?.username)
                                        bindStringNullable("project", (ucloudMetadata.workspace as? WalletOwner.Project)?.projectId)
                                    }
                                )
                            } else {
                                session.prepareStatement(
                                    """
                                        delete from ucloud_storage_quota_locked
                                        where
                                            username is not distinct from :username
                                            and project_id is not distinct from :project
                                            and category is not distinct from :category
                                    """
                                ).useAndInvokeAndDiscard(
                                    prepare = {
                                        bindString("category", ucloudMetadata.product.category)
                                        bindStringNullable("username", (ucloudMetadata.workspace as? WalletOwner.User)?.username)
                                        bindStringNullable("project", (ucloudMetadata.workspace as? WalletOwner.Project)?.projectId)
                                    }
                                )
                            }
                        }
                    } catch (ex: Throwable) {
                        log.warn("Caught exception while scanning ($taskId): ${ex.toReadableStacktrace()}")
                    }
                }
            }
        }

        serviceContext.ipcServerOptional?.addHandler(StorageScanIpc.requestScan.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            requestScan(request.id)
        })
    }

    private suspend fun usageScanFetchDriveIdsForScanGraal(
        driveIds: List<Long>,
        lastScans: HashMap<Long, Long>,
        now: Long
    ) {
        if (driveIds.isNotEmpty()) {
            dbConnection.withSession { session ->
                session.prepareStatement(
                    """
                        select drive_id, last_scan
                        from ucloud_storage_scans
                        where drive_id = some(:drive_ids)
                    """
                ).useAndInvokeGraal(
                    prepare = {
                        bindList("drive_ids", driveIds, SQL_TYPE_HINT_INT8)
                    },
                    readRow = { row ->
                        lastScans[row.getLong(0)!!] = row.getLong(1)!!
                    }
                )

                session.prepareStatement(
                    """
                        with data as (
                            select unnest(:drive_ids) drive_id, :now now
                        )
                        insert into ucloud_storage_scans(drive_id, last_scan)
                        select drive_id, now
                        from data
                        on conflict (drive_id) do update set
                            last_scan = excluded.last_scan
                    """
                ).useAndInvokeAndDiscardGraal(
                    prepare = {
                        bindList("drive_ids", driveIds, SQL_TYPE_HINT_INT8)
                        bindLong("now", now)
                    }
                )
            }
        }
    }

    suspend fun requestScan(driveId: Long) {
        val drive = pathConverter.locator.resolveDrive(driveId, allowMaintenanceMode = true) ?: return
        scanQueue.send(ScanTask(drive))
    }

    companion object : Loggable {
        override val log = logger()
        const val ONE_MONTH_MILLIS = 1000L * 60 * 60 * 24 * 30
    }

}

object StorageScanIpc : IpcContainer("storage_scan_plugin") {
    val requestScan = updateHandler("requestScan", FindByLongId.serializer(), Unit.serializer())
}

private object ScanningScope : CoroutineScope {
    private val job = SupervisorJob()
    @OptIn(DelicateCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = job + newFixedThreadPoolContext(
        Runtime.getRuntime().availableProcessors(),
        "StorageScan"
    )
}
