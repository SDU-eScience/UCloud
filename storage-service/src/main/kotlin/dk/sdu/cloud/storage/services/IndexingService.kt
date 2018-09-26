package dk.sdu.cloud.storage.services

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.storage.util.parent
import dk.sdu.cloud.storage.util.toCreatedEvent
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import java.util.*

/**
 * Service responsible for handling operations related to indexing
 */
class IndexingService<Ctx : FSUserContext>(
    private val runnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: CoreFileSystemService<Ctx>,
    private val storageEventProducer: StorageEventProducer
) {
    fun verifyKnowledge(ctx: Ctx, files: List<String>): List<Boolean> {
        val parents = files.asSequence().map { it.parent() }.toSet()
        val knowledgeByParent = parents.map { it to hasReadInDirectory(ctx, it) }.toMap()
        return files.map { knowledgeByParent[it.parent()]!! }
    }

    private fun hasReadInDirectory(ctx: Ctx, directoryPath: String): Boolean {
        return try {
            // TODO We don't actually have to list anything in the directory. Would be faster without
            fs.listDirectory(ctx, directoryPath, setOf(FileAttribute.INODE))
            true
        } catch (ex: FSException) {
            when (ex) {
                is FSException.PermissionException, is FSException.NotFound -> false
                else -> throw ex
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
     * This method will quickly verify if the roots exist and return the result of that in [Pair.first].
     *
     * Afterwards the algorithm will continue to perform the diffing and launch events based on
     * this, progress can be tracked via [Pair.second].
     */
    fun runDiffOnRoots(
        rootToReference: Map<String, List<EventMaterializedStorageFile>>
    ): Pair<Map<String, Boolean>, Job> {
        val ctx = runnerFactory(SERVICE_USER)
        val roots = rootToReference.keys

        val shouldContinue = try {
            roots.map { root ->
                val isValid = fs
                    .statOrNull(ctx, root, setOf(FileAttribute.FILE_TYPE))
                    ?.takeIf { it.fileType == FileType.DIRECTORY } != null

                root to isValid
            }.toMap()
        } catch (ex: Exception) {
            ctx.close()
            throw ex
        }

        val job = launch {
            try {
                rootToReference.map { (root, reference) ->
                    log.debug("Calculating diff for $root")
                    val diff = calculateDiff(ctx, root, reference).diff
                    if (diff.isNotEmpty()) {
                        log.info("Diff for $root caused ${diff.size} correction events to be emitted")
                        if (log.isDebugEnabled) log.debug(diff.toString())
                    }

                    diff.forEach { storageEventProducer.emit(it) }
                }
            } catch (ex: Exception) {
                // Note: we don't bubble up the exception to anyone else
                log.warn("Caught exception while diffing directories:")
                log.warn(rootToReference.toString())
                log.warn(ex.stackTraceToString())
            } finally {
                ctx.close()
            }
        }

        return Pair(shouldContinue, job)
    }

    /**
     * Calculates which events are missing from the [reference] folder
     *
     * It is assumed that the [ctx] can read the entirety of [directoryPath]
     */
    fun calculateDiff(ctx: Ctx, directoryPath: String, reference: List<EventMaterializedStorageFile>): DirectoryDiff {
        val realDirectory = try {
            fs.listDirectory(ctx, directoryPath, STORAGE_EVENT_MODE)
        } catch (ex: FSException.NotFound) {
            val invalidatedEvent = StorageEvent.Invalidated(
                "invalid-id-" + UUID.randomUUID().toString(),
                directoryPath,
                SERVICE_USER,
                System.currentTimeMillis()
            )

            return DirectoryDiff(shouldContinue = false, diff = listOf(invalidatedEvent))
        } catch (ex: FSException.BadRequest) {
            // Will be thrown if the root is not a directory. The diff will be caught in the parent.
            return DirectoryDiff(shouldContinue = false, diff = emptyList())
        }

        val realByPath = realDirectory.associateBy { it.path }
        val realById = realDirectory.associateBy { it.inode }
        val referenceById = reference.associateBy { it.id }
        val allFileIds = referenceById.keys + realById.keys

        val eventCollector = ArrayList<StorageEvent>()

        val deletedFiles = referenceById.filter { it.key !in realById }
        eventCollector.addAll(deletedFiles.map {
            StorageEvent.Invalidated(it.value.id, it.value.path, it.value.owner, System.currentTimeMillis())
        })

        // Note: We don't emit any deleted events. That would cause problems for clients that assume that
        // create/deletes are always correctly ordered. During this diffing process we could easily get
        // "create (A) -> create (B) -> delete (A)" in the case of a file moving from A to B.
        // Using Invalidated events instead force the client to consider the path, and nothing more (it won't
        // delete based on file ID).

        val newFiles = realById.filter { it.key !in referenceById }
        eventCollector.addAll(newFiles.filter { it.value.fileType == FileType.FILE }.map { it.value.toCreatedEvent() })

        // For directories created here we can be pretty sure that the reference system does _not_ already have
        // them. We need to traverse those
        eventCollector.addAll(
            newFiles.filter { it.value.fileType == FileType.DIRECTORY }.flatMap { (_, directory) ->
                fs.tree(ctx, directory.path, STORAGE_EVENT_MODE).map { it.toCreatedEvent() }
            }
        )

        val filesStillPresent = allFileIds.filter { it in realById && it in referenceById }
        eventCollector.addAll(
            filesStillPresent.flatMap { id ->
                val realFile = realById[id]!!
                val referenceFile = referenceById[id]!!
                assert(referenceFile.id == realFile.inode)

                val events = ArrayList<StorageEvent>()
                if (referenceFile.path != realFile.path) {
                    events.add(
                        StorageEvent.Moved(
                            realFile.inode,
                            realFile.path,
                            realFile.owner,
                            realFile.timestamps.modified,
                            referenceFile.path
                        )
                    )

                    if (realFile.fileType == FileType.DIRECTORY) {
                        // Need to invalidate and re-index
                        //
                        // We don't attempt to just rename children since events are likely missing due to parent being
                        // out of sync.
                        events.add(
                            StorageEvent.Invalidated(
                                realFile.inode,
                                referenceFile.path,
                                realFile.owner,
                                realFile.timestamps.modified
                            )
                        )

                        events.addAll(fs.tree(ctx, realFile.path, STORAGE_EVENT_MODE).map { it.toCreatedEvent() })
                    }
                }

                if (
                    referenceFile.annotations.sorted() != realFile.annotations.sorted() ||
                    referenceFile.fileType != realFile.fileType ||
                    referenceFile.owner != realFile.owner ||
                    referenceFile.sensitivityLevel != realFile.sensitivityLevel
                ) {
                    // Note: The file type can only be wrong if the client has made an incorrect assumption
                    // (due to missing information). We do not need to perform traversals on the directory (if applicable)
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