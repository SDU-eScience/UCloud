package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.Comparison
import dk.sdu.cloud.indexing.api.ComparisonOperator
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.indexing.util.depth
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.BulkByScrollResponse
import org.elasticsearch.index.reindex.DeleteByQueryAction
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import dk.sdu.cloud.indexing.services.ElasticIndexedFile
import org.elasticsearch.ElasticsearchException


@Suppress("BlockingMethodInNonBlockingContext")
class FileSystemScanner(
    private val elastic: RestHighLevelClient,
    private val query: ElasticQueryService,
    private val cephFsRoot: String,
    private val stats: FastDirectoryStats?
) {
    private val pool = Executors.newFixedThreadPool(16).asCoroutineDispatcher()

    private fun updateDocWithNewFile(file: ElasticIndexedFile): UpdateRequest {
        return UpdateRequest(FILES_INDEX, file.path).apply {
            val writeValueAsBytes = defaultMapper.writeValueAsBytes(file)
            doc(writeValueAsBytes, XContentType.JSON)
            docAsUpsert(true)
        }
    }

    private fun deleteDocWithFile(cloudPath: String): DeleteRequest {
        return DeleteRequest(FILES_INDEX, cloudPath)
    }

    suspend fun runScan() {
        withContext(pool) {
            launch {
                submitScan(File(cephFsRoot, "home").absoluteFile)
            }.join()
            launch {
                submitScan(File(cephFsRoot, "projects").absoluteFile)
            }.join()
        }
    }

    inner class BulkRequestBuilder {
        private var bulkCount = 0
        private var bulk = BulkRequest()

        fun flush() {
            if (bulkCount > 0) {
                elastic.bulk(bulk, RequestOptions.DEFAULT)
                bulkCount = 0
                bulk = BulkRequest()
            }
        }

        private fun flushIfNeeded() {
            if (bulkCount >= 100) {
                flush()
            }
        }

        fun add(updateRequest: UpdateRequest) {
            bulkCount++
            bulk.add(updateRequest)
            flushIfNeeded()
        }

        fun add(deleteRequest: DeleteRequest) {
            bulkCount++
            bulk.add(deleteRequest)
            flushIfNeeded()
        }

    }

    private suspend fun submitScan(path: File, upperLimitOfEntries: Long = Long.MAX_VALUE) {
        log.debug("Scanning: ${path.toCloudPath()} (${path})")

        val thisFileInIndex = run {
            val source =
                elastic.get(GetRequest(FILES_INDEX, path.toCloudPath()), RequestOptions.DEFAULT)?.sourceAsString
            if (source != null) {
                defaultMapper.readValue<ElasticIndexedFile>(source)
            } else {
                null
            }
        }

        var newUpperLimitOfEntries = 1L
        if (path.isDirectory) {
            val rctime = runCatching { stats?.getRecursiveTime(path.absolutePath) }.getOrNull()
            val (shouldContinue, limit) = shouldContinue(path, upperLimitOfEntries)
            newUpperLimitOfEntries = limit

            // We must continue if rctime does not match ceph
            if (rctime != null && thisFileInIndex != null && thisFileInIndex.rctime == rctime && !shouldContinue) {
                log.debug("${path.toCloudPath()} already up-to-date")
                return
            }
        }

        val fileList = (path.listFiles() ?: emptyArray())
        val files = fileList.map { it.toElasticIndexedFile() }.associateBy { it.path }
        val filesInIndex = query.query(
            FileQuery(
                listOf(path.toCloudPath()),
                fileDepth = AnyOf.with(
                    Comparison(path.toCloudPath().depth() + 1, ComparisonOperator.EQUALS)
                )
            )
        ).items.associateBy { it.path }

        val bulk = BulkRequestBuilder()
        filesInIndex.values.asSequence()
            .filter { it.path !in files }
            .forEach {
                bulk.add(deleteDocWithFile(it.path))
                val queryDeleteRequest = DeleteByQueryRequest(FILES_INDEX)
                queryDeleteRequest.setConflicts("proceed")
                queryDeleteRequest.setQuery(
                    QueryBuilders.wildcardQuery(
                        "_id",
                        "${it.path}/*"
                    )
                )
                queryDeleteRequest.batchSize = 100
                try {
                    //We only delete 100 at a time to reduce stress. Redo until all matching search is deleted
                    var moreTodelete = true
                    while (moreTodelete) {
                        val response = elastic.deleteByQuery(queryDeleteRequest, RequestOptions.DEFAULT)
                        if (response.deleted == 0L) moreTodelete = false
                    }
                } catch (ex: ElasticsearchException) {
                    log.warn("Deletion of ${it.path}/* , failed")
                }
            }

        files.values.asSequence()
            .filter { it.path !in filesInIndex }
            .forEach { bulk.add(updateDocWithNewFile(it)) }

        if (thisFileInIndex == null) {
            bulk.add(updateDocWithNewFile(path.toElasticIndexedFile()))
        }

        bulk.flush()

        fileList.mapNotNull { file ->
            withContext(pool) {
                if (file.isDirectory) {
                    launch {
                        submitScan(file, newUpperLimitOfEntries)
                    }
                } else {
                    null
                }
            }
        }.joinAll()
    }

    private fun File.toElasticIndexedFile(): ElasticIndexedFile {
        return ElasticIndexedFile(
            toCloudPath(),
            length(),
            if (isDirectory) FileType.DIRECTORY else FileType.FILE,
            runCatching { stats?.getRecursiveTime(absolutePath) }.getOrNull()
        )
    }

    data class ShouldContinue(val shouldContinue: Boolean, val newUpperLimitOfEntries: Long)

    private fun shouldContinue(path: File, upperLimitOfEntries: Long): ShouldContinue {
        if (path.isFile) return ShouldContinue(true, 1)
        if (stats == null) return ShouldContinue(true, upperLimitOfEntries)
        if (upperLimitOfEntries < 100) return ShouldContinue(true, upperLimitOfEntries)
        val recursiveEntryCount = stats.getRecursiveEntryCount(path.absolutePath)
        if (recursiveEntryCount > upperLimitOfEntries) return ShouldContinue(true, recursiveEntryCount)

        val fileStats = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(listOf(path.toCloudPath())),
                size = NumericStatisticsRequest(calculateSum = true)
            )
        )

        val fileCount = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(
                    listOf(path.toCloudPath()),
                    fileTypes = AnyOf.with(FileType.FILE)
                )
            )
        )

        val dirCount = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(
                    listOf(path.toCloudPath()),
                    fileTypes = AnyOf.with(FileType.DIRECTORY)
                )
            )
        )

        val size = fileStats.size!!
        val recursiveFiles = fileCount.count
        val recursiveSubDirs = dirCount.count

        if (recursiveEntryCount != recursiveFiles + recursiveSubDirs) {
            log.info("Entry count is different ($recursiveEntryCount != $recursiveFiles + $recursiveSubDirs)")
            return ShouldContinue(true, recursiveEntryCount)
        }

        val actualRecursiveSize = stats.getRecursiveSize(path.absolutePath)
        val sum = size.sum
        if (sum!!.toLong() != actualRecursiveSize) {
            val percentage = if (sum.toLong() == 0L) {
                1.0
            } else {
                1 - (actualRecursiveSize / sum)
            }

            if (percentage >= abs(0.05)) {
                log.info("Size is different $actualRecursiveSize != $sum")
                return ShouldContinue(true, recursiveEntryCount)
            }
        }

        val actualRecursiveFiles = stats.getRecursiveFileCount(path.absolutePath)
        val actualRecursiveSubDirs = stats.getRecursiveDirectoryCount(path.absolutePath)
        if (recursiveSubDirs != actualRecursiveSubDirs) {
            log.info("Sub dirs is different ${recursiveSubDirs} ${actualRecursiveSubDirs}")
            return ShouldContinue(true, recursiveEntryCount)
        }

        if (recursiveFiles != actualRecursiveFiles) {
            log.info("Recursive files is different $recursiveFiles $actualRecursiveFiles")
            return ShouldContinue(true, recursiveEntryCount)
        }

        log.info("Skipping $path ($recursiveEntryCount entries has been skipped)")
        return ShouldContinue(false, recursiveEntryCount)
    }

    private fun File.toCloudPath(): String {
        return "/" + absolutePath.normalize().removePrefix(cephFsRoot).removePrefix("/")
    }

    companion object : Loggable {
        override val log: Logger = logger()
        internal const val FILES_INDEX = "files"
    }
}
