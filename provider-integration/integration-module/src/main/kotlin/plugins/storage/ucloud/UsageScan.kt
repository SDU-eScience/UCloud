package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.ProductReferenceV2
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.handler
import dk.sdu.cloud.plugins.AllocationPlugin
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.*
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.coroutines.CoroutineContext

class UsageScan(
    private val pluginName: String,
    private val pathConverter: PathConverter,
    private val fastDirectoryStats: FastDirectoryStats,
) {
    private data class ScanTask(val driveInfo: DriveAndSystem)
    private val scanQueue = Channel<ScanTask>(Channel.BUFFERED)

    fun init() {
        ProcessingScope.launch {
            val taskName = "storage-scan-ucloud"
            whileGraal({ isActive }) {
                val start = Time.now()
                Prometheus.countBackgroundTask(taskName)
                try {
                    scanAll(ignoreTimeSinceLastScan = false)
                } finally {
                    val duration = Time.now() - start
                    Prometheus.measureBackgroundDuration(taskName, duration)
                    delay(60_000 - duration)
                }
            }
        }

        repeat(8) { taskId ->
            ScanningScope.launch {
                log.info("Scan loop has started!")
                var loopActive = true
                whileGraal({ isActive && loopActive }) {
                    try {
                        val task = scanQueue.receiveCatching().getOrNull() ?: run {
                            log.error("Breaking scan loop! This should probably not happen unless we are shutting down.")
                            loopActive = false
                            return@whileGraal
                        }
                        val taskName = "storage_scan"
                        Prometheus.countBackgroundTask(taskName)

                        val start = Time.now()
                        try {
                            val driveInfo = task.driveInfo
                            val driveRoot = driveInfo.driveRoot ?: return@whileGraal
                            if (driveInfo.inMaintenanceMode) return@whileGraal
                            // TODO This blocks the thread while we are collecting the size.
                            val size = fastDirectoryStats.getRecursiveSize(driveRoot, allowSlowPath = true)
                                ?: return@whileGraal

                            pathConverter.locator.updateDriveSize(driveInfo.drive.ucloudId, size)

                            val ucloudMetadata = pathConverter.locator.fetchMetadataForDrive(driveInfo.drive.ucloudId)
                                ?: return@whileGraal

                            reportUsage(
                                ucloudMetadata.workspace,
                                ucloudMetadata.product,
                                size,
                                minutesUsed = null,
                                scope = task.driveInfo.drive.ucloudId.toString(),
                            )
                        } finally {
                            val end = Time.now()
                            Prometheus.measureBackgroundDuration(taskName, end - start)
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

        serviceContext.ipcServerOptional?.addHandler(StorageScanIpc.reportUsage.handler { user, request ->
            if (user.uid != 0) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

            reportUsage(
                walletOwnerFromOwnerString(request.workspace),
                ProductReferenceV2(request.productName, request.productCategory, ""),
                request.usageInBytes,
                minutesUsed = null,
                scope = request.driveId.toString(),
            )
        })
    }

    private suspend fun usageScanFetchDriveIdsForScanGraal(
        driveIds: List<Long>,
        ignoreTimeSinceLastScan: Boolean,
    ): List<Long> {
        if (driveIds.isNotEmpty()) {
            val now = Time.now()
            val minimumTime = if (ignoreTimeSinceLastScan) 0L else now - (1000L * 60 * 60 * 8)
            val lastScans = HashMap<Long, Long>()

            return dbConnection.withSession { session ->
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

                val toUpdate = lastScans.filter { ignoreTimeSinceLastScan || it.value <= minimumTime }.map { it.key }

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
                        bindList("drive_ids", toUpdate, SQL_TYPE_HINT_INT8)
                        bindLong("now", now)
                    }
                )

                toUpdate
            }
        }
        return emptyList()
    }

    private suspend fun scanAll(ignoreTimeSinceLastScan: Boolean) {
        try {
            var next: String? = null
            var innerActive = true

            var scansSubmitted = 0
            whileGraal({ innerActive }) {
                val page = pathConverter.locator.enumerateDrives(next = next)
                val driveIds = page.items.map { it.drive.ucloudId }
                val toScan = usageScanFetchDriveIdsForScanGraal(driveIds, ignoreTimeSinceLastScan)

                toScan.forEachGraal { item ->
                    val toSubmit = page.items.find { it.drive.ucloudId == item }
                    if (toSubmit != null) {
                        scansSubmitted++
                        scanQueue.send(ScanTask(toSubmit))
                    }
                }

                val nextToken = page.next
                if (nextToken == null) {
                    innerActive = false
                } else {
                    next = nextToken
                }
            }

            if (scansSubmitted >= 0) {
                log.info("Started $scansSubmitted drive scans!")
            }
        } catch (ex: Throwable) {
            log.warn("Caught exception while scanning usage for $pluginName: ${ex.toReadableStacktrace()}")
        }
    }

    private suspend fun reportCachedDataToUCloud() {
        data class Entry(
            val collectionId: Long,
            val driveOwner: String,
            val driveOwnerIsUser: Boolean,
            val productCategory: String,
            val sizeInBytes: Long,
        )

        val entries = ArrayList<Entry>()

        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select collection_id, drive_owner, drive_owner_is_user, product_category, size_in_bytes
                    from ucloud_storage_drives
                """
            ).useAndInvoke(
                prepare = {},
                readRow = { row ->
                    entries.add(Entry(
                        row.getLong(0) ?: return@useAndInvoke,
                        row.getString(1) ?: return@useAndInvoke,
                        row.getBoolean(2) ?: return@useAndInvoke,
                        row.getString(3) ?: return@useAndInvoke,
                        row.getLong(4) ?: return@useAndInvoke,
                    ))
                }
            )
        }

        log.info("Found ${entries.size} drives to report data for")

        for ((index, entry) in entries.withIndex()) {
            if (index % 1000 == 0) {
                log.info("Reported ${index}/${entries.size}")
            }
            reportUsage(
                if (entry.driveOwnerIsUser) {
                    WalletOwner.User(entry.driveOwner)
                } else {
                    WalletOwner.Project(entry.driveOwner)
                },
                ProductReferenceV2(entry.productCategory, entry.productCategory, loadedConfig.core.providerId),
                entry.sizeInBytes,
                null,
                entry.collectionId.toString()
            )
        }

        log.info("All ${entries.size} have been reported to UCloud")
    }

    suspend fun requestScan(driveId: Long) {
        if (driveId <= 0L) {
            if (driveId == -100L) {
                reportCachedDataToUCloud()
            } else {
                scanAll(ignoreTimeSinceLastScan = true)
            }
        } else {
            val drive = pathConverter.locator.resolveDrive(driveId, allowMaintenanceMode = true) ?: return
            scanQueue.send(ScanTask(drive))
        }
    }

    suspend fun notifyAccounting(notification: AllocationPlugin.Message) {
        val walletOwner = notification.owner.toResourceOwner().toWalletOwner()
        dbConnection.withSession { session ->
            if (notification.locked) {
                session.prepareStatement(
                    """
                        insert into ucloud_storage_quota_locked (scan_id, category, username, project_id)
                        values ('unused', :category, :username, :project)
                    """
                ).useAndInvokeAndDiscard(
                    prepare = {
                        bindString("category", notification.category.name)
                        bindStringNullable("username", (walletOwner as? WalletOwner.User)?.username)
                        bindStringNullable("project", (walletOwner as? WalletOwner.Project)?.projectId)
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
                        bindString("category", notification.category.name)
                        bindStringNullable("username", (walletOwner as? WalletOwner.User)?.username)
                        bindStringNullable("project", (walletOwner as? WalletOwner.Project)?.projectId)
                    }
                )
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

object StorageScanIpc : IpcContainer("storage_scan_plugin") {
    val requestScan = updateHandler("requestScan", FindByLongId.serializer(), Unit.serializer())
    val reportUsage = updateHandler("reportUsage", StorageScanReportUsage.serializer(), Unit.serializer())
}

@Serializable
data class StorageScanReportUsage(
    val workspace: String,
    val productName: String,
    val productCategory: String,
    val driveId: Long,
    val usageInBytes: Long,
)

private object ScanningScope : CoroutineScope {
    private val job = SupervisorJob()
    @OptIn(DelicateCoroutinesApi::class)
    override val coroutineContext: CoroutineContext = job + newFixedThreadPoolContext(
        Runtime.getRuntime().availableProcessors(),
        "StorageScan"
    )
}
