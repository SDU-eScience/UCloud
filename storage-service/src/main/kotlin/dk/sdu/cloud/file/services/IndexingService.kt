package dk.sdu.cloud.file.services

import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.KnowledgeMode
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.creator
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.fileType
import dk.sdu.cloud.file.api.ownSensitivityLevel
import dk.sdu.cloud.file.api.ownerName
import dk.sdu.cloud.file.api.parent
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.processors.ScanRequest
import dk.sdu.cloud.file.processors.ScanStreams
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.file.util.toCreatedEvent
import dk.sdu.cloud.file.util.toMovedEvent
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Service responsible for handling operations related to indexing
 */
class IndexingService<Ctx : FSUserContext>(
    private val runnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val storageEventProducer: StorageEventProducer,
    private val aclService: AclService<*>,
    private val eventStreamService: EventStreamService
) {
    private val scanRequestProducer by lazy { eventStreamService.createProducer(ScanStreams.stream) }

    suspend fun verifyKnowledge(
        ctx: Ctx,
        files: List<String>,
        mode: KnowledgeMode = KnowledgeMode.List()
    ): List<Boolean> {
        return when (mode) {
            is KnowledgeMode.List -> {
                val parents = files.asSequence().map { it.parent() }.toSet()
                //val knowledgeByParent = parents.map { it to fs.checkPermissions(ctx, it, requireWrite = false) }.toMap()
                val knowledgeByParent =
                    parents.map { it to aclService.hasPermission(it, ctx.user, AccessRight.READ) }.toMap()

                files.map { knowledgeByParent[it.parent()]!! }
            }

            is KnowledgeMode.Permission -> {
                files.map {
                    aclService.hasPermission(
                        it,
                        ctx.user,
                        if (mode.requireWrite) AccessRight.WRITE else AccessRight.READ
                    )
                }
            }
        }
    }

    data class DirectoryDiff(
        val shouldContinue: Boolean,
        val diff: List<StorageEvent>
    )

    /**
     * Runs the diff algorithm on several roots.
     *
     * This method will create its own context, to avoid problems where this method will have its
     * interpreter closed prematurely. This would happen in the normal tryWithFS { ... } workflow.
     * It will run as the [SERVICE_USER] it is expected that this user can read all files.
     *
     * This method will quickly verify if the roots exist and return the result.
     */
    suspend fun submitScan(rootToReference: Map<String, List<StorageFile>>): Map<String, Boolean> {
        val areRootsValid = runnerFactory.withContext(SERVICE_USER) { ctx ->
            rootToReference.keys.map { root ->
                val isValid = fs
                    .statOrNull(ctx, root, setOf(FileAttribute.FILE_TYPE))
                    ?.takeIf { it.fileType == FileType.DIRECTORY } != null

                root to isValid
            }.toMap()
        }

        scanRequestProducer.produce(ScanRequest(rootToReference))
        return areRootsValid
    }

    suspend fun runScan(rootToReference: Map<String, List<StorageFile>>) {
        runnerFactory.withContext(SERVICE_USER) { ctx ->
            try {
                rootToReference.map { (root, reference) ->
                    log.trace("Calculating diff for $root")
                    val diff = calculateDiff(ctx, root, reference).diff
                    if (diff.isNotEmpty()) {
                        log.info("Diff for $root caused ${diff.size} correction events to be emitted")
                        if (log.isTraceEnabled) log.trace(diff.toString())
                    }

                    storageEventProducer.produce(diff)
                }
            } catch (ex: Exception) {
                // Note: we don't bubble up the exception to anyone else
                log.warn("Caught exception while diffing directories:")
                log.warn(rootToReference.toString())
                log.warn(ex.stackTraceToString())
            }
        }
    }

    /**
     * Calculates which events are missing from the [reference] folder
     *
     * It is assumed that the [ctx] can read the entirety of [directoryPath]
     */
    suspend fun calculateDiff(
        ctx: Ctx,
        directoryPath: String,
        reference: List<StorageFile>
    ): DirectoryDiff {
        val realDirectory = try {
            fs.listDirectory(ctx, directoryPath, STORAGE_EVENT_MODE)
        } catch (ex: FSException.NotFound) {
            val invalidatedEvent = StorageEvent.Invalidated(
                path = directoryPath,
                timestamp = System.currentTimeMillis()
            )

            return DirectoryDiff(
                shouldContinue = false,
                diff = listOf(invalidatedEvent)
            )
        } catch (ex: FSException.BadRequest) {
            // Will be thrown if the root is not a directory. The diff will be caught in the parent.
            return DirectoryDiff(shouldContinue = false, diff = emptyList())
        }

        val realByPath = realDirectory.associateBy { it.path }
        val realById = realDirectory.associateBy { it.inode }
        val referenceById = reference.associateBy { it.fileId }
        val allFileIds = referenceById.keys + realById.keys

        val eventCollector = ArrayList<StorageEvent>()

        val deletedFiles = referenceById.filter { it.key !in realById }
        log.trace("The following files were deleted: ${deletedFiles.values.map { it.path }}")
        eventCollector.addAll(deletedFiles.map {
            StorageEvent.Invalidated(
                path = it.value.path,
                timestamp = System.currentTimeMillis()
            )
        })

        // Note: We don't emit any deleted events. That would cause problems for clients that assume that
        // create/deletes are always correctly ordered. During this diffing process we could easily get
        // "create (A) -> create (B) -> delete (A)" in the case of a file moving from A to B.
        // Using Invalidated events instead force the client to consider the path, and nothing more (it won't
        // delete based on file ID).

        val newFiles = realById.filter { it.key !in referenceById }
        log.trace("The following files are new (not in reference): ${newFiles.values.map { it.path }}")
        eventCollector.addAll(newFiles.filter { it.value.fileType == FileType.FILE }.map { it.value.toCreatedEvent() })

        // For directories created here we can be pretty sure that the reference system does _not_ already have
        // them. We need to traverse those
        eventCollector.addAll(
            newFiles.filter { it.value.fileType == FileType.DIRECTORY }.flatMap { (_, directory) ->
                log.trace("Looking at $directory")
                val result = fs.tree(ctx, directory.path, STORAGE_EVENT_MODE).map { it.toCreatedEvent() }
                log.trace("Done with $directory")
                result
            }
        )

        val filesStillPresent = allFileIds.filter { it in realById && it in referenceById }
        eventCollector.addAll(
            filesStillPresent.flatMap { id ->
                val realFile = realById[id]!!
                val referenceFile = referenceById[id]!!
                assert(referenceFile.fileId == realFile.inode)

                val events = ArrayList<StorageEvent>()
                if (referenceFile.path != realFile.path) {
                    log.trace("Path difference for ${realFile.path}")
                    events.add(
                        realFile.toMovedEvent(referenceFile.path)
                    )

                    if (realFile.fileType == FileType.DIRECTORY) {
                        log.trace("File type difference for ${realFile.fileType}")
                        // Need to invalidate and re-index
                        //
                        // We don't attempt to just rename children since events are likely missing due to parent being
                        // out of sync.
                        events.add(
                            StorageEvent.Invalidated(
                                path = referenceFile.path,
                                timestamp = realFile.timestamps.modified
                            )
                        )

                        events.addAll(fs.tree(ctx, realFile.path, STORAGE_EVENT_MODE).map { it.toCreatedEvent() })
                    }
                }

                if (
                    referenceFile.fileType != realFile.fileType ||
                    referenceFile.ownerName != realFile.creator ||
                    referenceFile.ownSensitivityLevel != realFile.sensitivityLevel ||
                    referenceFile.creator != realFile.creator
                ) {
                    log.trace("Metadata difference for ${realFile.path}")

                    // Note: The computed sensitivity level is ignored when performing diff checks. We only look for
                    // information in the StorageEvents which would be the ownSensitivityLevel only.

                    // Note: The file type can only be wrong if the client has made an incorrect assumption
                    // (due to missing information). We do not need to perform traversals on the
                    // directory (if applicable)
                    events.add(realFile.toCreatedEvent())
                }

                events
            }
        )

        // Detect paths that have been invalidated (for valid reasons) but have since reference gotten a new file at
        // same path
        eventCollector.addAll(
            eventCollector
                .asSequence()
                .filterIsInstance<StorageEvent.Invalidated>()
                .mapNotNull { realByPath[it.path]?.toCreatedEvent() }
                .toList()
        )

        return DirectoryDiff(
            shouldContinue = true,
            diff = eventCollector
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
