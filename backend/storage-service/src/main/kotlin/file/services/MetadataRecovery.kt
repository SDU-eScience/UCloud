package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageFileAttribute
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.minutes
import kotlin.time.seconds

class MetadataRecoveryService<Ctx : FSUserContext>(
    private val scope: CoroutineScope,
    private val distributedLockFactory: DistributedLockFactory,

    private val fs: CoreFileSystemService<Ctx>,
    private val fsRunner: FSCommandRunnerFactory<Ctx>,

    private val db: AsyncDBSessionFactory,
    private val dao: MetadataDao
) {
    fun startProcessing(): Job {
        return scope.launch {
            val lock = distributedLockFactory.create("metadata-recovery-lock", duration = 60_000)

            while (true) {
                try {
                    process(lock)
                } catch (ex: Throwable) {
                    log.warn(ex.stackTraceToString())
                }

                delay(15000 + Random.nextLong(5000))
            }
        }
    }

    suspend fun verify(paths: List<String>) {
        db.withTransaction { session ->
            fsRunner.withContext(SERVICE_USER) { ctx ->
                paths.forEach { path ->
                    val stat = fs.statOrNull(ctx, path, setOf(StorageFileAttribute.path, StorageFileAttribute.fileType))
                    if (stat == null) {
                        log.info("Metadata no longer exists for $path")
                        dao.deleteByPrefix(session, path)
                    } else {
                        if (stat.fileType == FileType.DIRECTORY) {
                            dao.findByPrefix(session, path, null, null).forEach { metadata ->
                                if (!fs.exists(ctx, metadata.path)) {
                                    dao.deleteByPrefix(session, metadata.path)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun process(lock: DistributedLock) {
        val didAcquire = lock.acquire()
        if (didAcquire) {
            log.info("This service has become the master responsible for metadata recovery!")
            while (true) {
                val fixDuration = measureTime {
                    db.withTransaction { session ->
                        dao.deleteStaleMovingAttributes(session)

                        val metadata = dao.findUnmanagedMetadata(session)
                        if (metadata.isNotEmpty()) {
                            fsRunner.withContext(SERVICE_USER) { ctx ->
                                metadata.forEach { (path, pathMovingTo) ->
                                    val beforeMovement = try {
                                        fs.stat(ctx, path, setOf(StorageFileAttribute.path))
                                    } catch (ex: FSException.NotFound) {
                                        null
                                    }

                                    val movingTo = try {
                                        fs.stat(ctx, pathMovingTo, setOf(StorageFileAttribute.path))
                                    } catch (ex: FSException.NotFound) {
                                        null
                                    }

                                    when {
                                        beforeMovement != null && movingTo == null -> {
                                            // Crash before movement
                                            dao.cancelMovement(session, listOf(path.normalize()))
                                        }

                                        beforeMovement == null && movingTo != null -> {
                                            // Crash before second database write
                                            dao.moveMetadata(session, path.normalize(), pathMovingTo.normalize())
                                        }

                                        beforeMovement == null && movingTo == null -> {
                                            // File is gone (could be a normal deletion)
                                            dao.removeEntry(session, path.normalize(), null, null)
                                        }

                                        beforeMovement != null && movingTo != null -> {
                                            // Both files exist
                                            // In this case we choose to keep the data on the original path. This at least
                                            // does not accidentally lose (potentially) important data.
                                            dao.cancelMovement(session, listOf(path.normalize()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (fixDuration >= 1.minutes) {
                    log.warn("Metadata fix took longer than one minute! Fix took: $fixDuration")
                    log.warn("We will probably lose master status")
                }

                if (!lock.renew(90_000)) {
                    log.warn("Lock was lost. We are no longer the master. Did update take too long?")
                    break
                }

                delay(max(0L, (30.seconds - fixDuration).inMilliseconds.toLong()))
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
