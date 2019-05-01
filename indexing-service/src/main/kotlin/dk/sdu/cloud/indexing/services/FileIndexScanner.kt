package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.DeliverMaterializedFileSystemRequest
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.indexing.util.depth
import dk.sdu.cloud.indexing.util.lazyAssert
import dk.sdu.cloud.indexing.util.parent
import dk.sdu.cloud.indexing.util.scrollThroughSearch
import dk.sdu.cloud.indexing.util.source
import dk.sdu.cloud.indexing.util.term
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mbuhot.eskotlin.query.compound.bool
import mbuhot.eskotlin.query.term.terms
import org.elasticsearch.client.RestHighLevelClient

private const val CHUNK_SIZE = 100

/**
 * Scans through the file indexes and together with the storage-service fixes inconsistencies
 *
 * The storage-service is responsible for performing the diffing and emitting events to fix inconsistencies.
 *
 * This service is responsible for delivering a reliable materialized view based on the [StorageEvent] stream.
 */
class FileIndexScanner(
    private val cloud: AuthenticatedClient,
    private val elasticClient: RestHighLevelClient
) {
    private val mapper = defaultMapper

    fun scan() {
        // We will end up traversing the entries it has just corrected for us.
        // Although, some are likely to be missed since we do a breadth-first scan.

        // TODO It is possible to have orphaned files in the index.
        // This can happen due to Invalidated not being keyed correctly (its not possible to key it correctly).
        // In that case we may invalidated a directory before all events in the queue for the affected files are
        // processed.
        //
        // These won't be delivered to the FS, and as a result they won't be deleted. We should probably just detect
        // them during a scan and delete them. I see no reason to notify the storage service about it (the event
        // stream was correct).

        runBlocking {
            val queueLock = Any()
            val queue = arrayListOf(HARDCODED_ROOT)
            while (queue.isNotEmpty()) {
                // Send up to 100 roots per request
                val queueInChunks = queue.chunked(CHUNK_SIZE).also { queue.clear() }

                val localJobs = queueInChunks.map { roots ->
                    async<Unit> {
                        val rootToMaterialized =
                            HashMap<String, List<StorageFile>>()
                        roots.groupBy { it.depth() }.forEach { (depth, directories) ->
                            val directoryResults = scanDirectoriesOfDepth(depth, directories)
                            rootToMaterialized.putAll(directoryResults)

                            if (directories.contains(HARDCODED_ROOT) && HARDCODED_ROOT !in rootToMaterialized) {
                                // We were scanning the root directory but no results came up. This means that the
                                // elastic index is more or less empty. In that case we should still add the root.
                                // This way the storage-service will actually reply with the correct index instead of
                                // assuming that everything is okay.
                                rootToMaterialized[HARDCODED_ROOT] = emptyList()
                            }
                        }

                        // TODO JSON payload can become gigantic with 100 roots.
                        // Will increase memory usage by _a lot_
                        val deliveryResponse =
                            FileDescriptions.deliverMaterializedFileSystem.call(
                                DeliverMaterializedFileSystemRequest(rootToMaterialized),
                                cloud
                            )

                        if (deliveryResponse !is IngoingCallResponse.Ok) {
                            throw FileIndexScanException.CouldNotDeliver("${deliveryResponse.statusCode}")
                        }

                        // Continue on all roots that the FS agrees on
                        val rootsToContinueOn = deliveryResponse.result.shouldContinue.filterValues { it }.keys
                        val newRoots = rootsToContinueOn.flatMap { root ->
                            rootToMaterialized[root]!!
                                .asSequence()
                                .filter { it.fileType == FileType.DIRECTORY && !it.link }
                                .map { it.path }
                                .toList()
                        }

                        synchronized(queueLock) {
                            queue.addAll(newRoots)
                        }
                    }
                }

                localJobs.awaitAll()
            }
        }
    }

    private fun scanDirectoriesOfDepth(
        depth: Int,
        directories: List<String>
    ): Map<String, List<StorageFile>> {
        lazyAssert("directories need to be of the same depth") {
            directories.all { it.depth() == depth }
        }

        log.debug("Scanning the following directories together:")
        log.debug('[' + directories.joinToString(", ") { "\"$it\"" } + ']')

        val items = ArrayList<StorageFile>()
        elasticClient.scrollThroughSearch<ElasticIndexedFile>(
            mapper,
            listOf(ElasticIndexingService.FILES_INDEX),
            builder = {
                source {
                    bool {
                        filter = listOf(
                            terms { ElasticIndexedFile.PATH_FIELD to directories },

                            // We want the files stored in it, thus we have to increment the depth by
                            // one (to get its direct children)
                            term { ElasticIndexedFile.FILE_DEPTH_FIELD to depth + 1 }
                        )
                    }
                }
            },

            handler = { items.add(it.toMaterializedFile()) }
        )

        return items.groupBy { it.path.parent() }
    }

    companion object : Loggable {
        override val log = logger()

        private const val HARDCODED_ROOT = "/home"
    }
}

/**
 * Exceptions that might occur during [FileIndexScanner.scan]
 */
sealed class FileIndexScanException(why: String, statusCode: HttpStatusCode) : RPCException(why, statusCode) {
    /**
     * Thrown if it is not possible to deliver the reference file system (from the file index) to the storage system
     */
    class CouldNotDeliver(why: String) :
        FileIndexScanException("Could not deliver reference system: $why", HttpStatusCode.BadGateway)
}
