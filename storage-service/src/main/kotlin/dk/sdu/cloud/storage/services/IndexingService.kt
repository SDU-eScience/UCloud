package dk.sdu.cloud.storage.services

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.storage.SERVICE_USER
import dk.sdu.cloud.storage.api.EventMaterializedStorageFile
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.util.FSException
import dk.sdu.cloud.storage.util.parent
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.launch
import java.util.*

/**
 * Service responsible for handling operations related to indexing
 */
class IndexingService<Ctx : FSUserContext>(
    private val fs: CoreFileSystemService<Ctx>,
    private val storageEventProducer: StorageEventProducer
) {
    fun verifyKnowledge(ctx: Ctx, files: List<String>): List<Boolean> {
        val parents = files.map { it.parent() }.toSet()
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
     * It is assumed that the [ctx] can read all of the roots stored [rootToReference].
     *
     * This method will quickly verify if the roots exist and return the result of that in [Pair.first].
     *
     * Afterwards the algorithm will continue to perform the diffing and launch events based on
     * this, progress can be tracked via [Pair.second].
     */
    fun runDiffOnRoots(
        ctx: Ctx,
        rootToReference: Map<String, List<EventMaterializedStorageFile>>
    ): Pair<Map<String, Boolean>, Job> {
        val roots = rootToReference.keys

        val shouldContinue = roots.map {
            val isValid = fs
                .statOrNull(ctx, it, setOf(FileAttribute.FILE_TYPE))
                ?.takeIf { it.fileType == FileType.DIRECTORY } != null

            it to isValid
        }.toMap()

        val job = launch {
            rootToReference.map { (root, reference) ->
                log.debug("Calculating diff for $root")
                val diff = calculateDiff(ctx, root, reference).diff
                if (diff.isNotEmpty()) {
                    log.info("Diff for $root caused ${diff.size} correction events to be emitted")
                    if (log.isDebugEnabled) log.debug(diff.toString())
                }

                diff.forEach { storageEventProducer.emit(it) }
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
        fun FileRow.toCreatedEvent() = StorageEvent.CreatedOrRefreshed(
            inode,
            path,
            owner,
            timestamps.created,
            fileType,
            timestamps,
            size,
            checksum,
            isLink,
            if (isLink) linkTarget else null,
            if (isLink) linkInode else null,
            annotations,
            sensitivityLevel
        )

        val realDirectory = try {
            fs.listDirectory(ctx, directoryPath, DIFF_MODE)
        } catch (ex: FSException.NotFound) {
            val invalidatedEvent = StorageEvent.Invalidated(
                "invalid-id-" + UUID.randomUUID().toString(),
                directoryPath,
                SERVICE_USER,
                System.currentTimeMillis()
            )

            return DirectoryDiff(shouldContinue = false, diff = listOf(invalidatedEvent))
        }

        val realById = realDirectory.associateBy { it.inode }
        val referenceById = reference.associateBy { it.id }
        val allFileIds = referenceById.keys + realById.keys

        val deletedFiles = referenceById.filter { it.key !in realById }
        val invalidatedEvents = deletedFiles.map {
            StorageEvent.Invalidated(it.value.id, it.value.path, it.value.owner, System.currentTimeMillis())
        }

        // Note: We don't emit any deleted events. That would cause problems for clients that assume that
        // create/deletes are always correctly ordered. During this diffing process we could easily get
        // "create (A) -> create (B) -> delete (A)" in the case of a file moving from A to B.
        // Using Invalidated events instead force the client to consider the path, and nothing more (it won't
        // delete based on file ID).

        val newFiles = realById.filter { it.key !in referenceById }
        val createdEvents = newFiles.map { it.value.toCreatedEvent() }
        // For directories created here we can be pretty sure that the reference system does _not_ already have
        // them. We need to traverse those
        val traversalEvents =
            newFiles.filter { it.value.fileType == FileType.DIRECTORY }.flatMap {
                fs.tree(ctx, it.value.path, DIFF_MODE).map { it.toCreatedEvent() }
            }

        val filesStillPresent = allFileIds.filter { it in realById && it in referenceById }
        val updatedEvents = filesStillPresent.flatMap { id ->
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

                    events.addAll(fs.tree(ctx, realFile.path, DIFF_MODE).map { it.toCreatedEvent() })
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

        return DirectoryDiff(
            shouldContinue = true,
            diff = invalidatedEvents + createdEvents + traversalEvents + updatedEvents
        )
    }

    companion object : Loggable {
        override val log = logger()

        private val DIFF_MODE = setOf(
            FileAttribute.FILE_TYPE,
            FileAttribute.INODE,
            FileAttribute.PATH,
            FileAttribute.TIMESTAMPS,
            FileAttribute.OWNER,
            FileAttribute.SIZE,
            FileAttribute.CHECKSUM,
            FileAttribute.IS_LINK,
            FileAttribute.LINK_TARGET,
            FileAttribute.LINK_INODE,
            FileAttribute.ANNOTATIONS,
            FileAttribute.SENSITIVITY
        )
    }
}