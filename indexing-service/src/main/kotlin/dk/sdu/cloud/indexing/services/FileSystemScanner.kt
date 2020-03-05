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
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.Executors

@Suppress("BlockingMethodInNonBlockingContext")
class FileSystemScanner(
    private val elastic: RestHighLevelClient,
    private val query: IndexQueryService,
    private val cephFsRoot: String,
    useCephStats: Boolean
) {
    private val stats: FastDirectoryStats? = if (useCephStats) CephFsFastDirectoryStats else null
    private val pool = Executors.newFixedThreadPool(16).asCoroutineDispatcher()

    private fun updateDocWithNewFile(file: ElasticIndexedFile): UpdateRequest {
        // TODO We have changed this to index by path and not by file ID
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
                submitScan(File(cephFsRoot, "home"))
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
        log.debug("Scanning: ${path.toCloudPath()}")

        val thisFileInIndex = run {
            val source =
                elastic.get(GetRequest(FILES_INDEX, path.toCloudPath()), RequestOptions.DEFAULT)?.sourceAsString
            if (source != null) {
                defaultMapper.readValue<ElasticIndexedFile>(source)
            } else {
                null
            }
        }

        val rctime = runCatching { stats?.getRecursiveTime(path.absolutePath) }.getOrNull()
        val (shouldContinue, newUpperLimitOfEntries) = shouldContinue(path, upperLimitOfEntries)

        // We must continue if rctime does not match ceph
        if (rctime != null && thisFileInIndex != null && thisFileInIndex.rctime == rctime && shouldContinue) {
            return
        }

        val fileList = (path.listFiles() ?: emptyArray())
        val files = fileList.map { it.toElasticIndexedFile() }.associateBy { it.path }
        val filesInIndex = query.query(
            FileQuery(
                listOf(path.toCloudPath()),
                fileDepth = AnyOf.with(
                    Comparison(path.toCloudPath().depth() + 1, ComparisonOperator.EQUALS)
                )
            ),
            null
        ).items.associateBy { it.path }

        val bulk = BulkRequestBuilder()
        filesInIndex.values.asSequence()
            .filter { it.path !in files }
            .map { bulk.add(deleteDocWithFile(it.path)) }

        files.values.asSequence()
            .filter { it.path !in filesInIndex }
            .forEach { bulk.add(updateDocWithNewFile(it)) }

        if (thisFileInIndex == null) {
            bulk.add(updateDocWithNewFile(path.toElasticIndexedFile()))
        }

        bulk.flush()

        fileList.map { file ->
            withContext(pool) {
                launch {
                    submitScan(file, newUpperLimitOfEntries)
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
        if (stats == null) return ShouldContinue(true, upperLimitOfEntries)
        if (upperLimitOfEntries < 100) return ShouldContinue(true, upperLimitOfEntries)
        val recursiveEntryCount = stats.getRecursiveEntryCount(path.absolutePath)
        if (recursiveEntryCount > upperLimitOfEntries) return ShouldContinue(true, recursiveEntryCount)

        val fileStats = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(listOf(path.toCloudPath())),
                size = NumericStatisticsRequest(calculateSum = true),
                recursiveFiles = true,
                recursiveSubDirs = true
            )
        )

        val size = fileStats.size!!
        val recursiveFiles = fileStats.recursiveFiles!!
        val recursiveSubDirs = fileStats.recursiveSubDirs!!

        if (recursiveEntryCount != recursiveFiles + recursiveSubDirs) {
            return ShouldContinue(true, recursiveEntryCount)
        }

        val actualRecursiveSize = stats.getRecursiveSize(path.absolutePath)
        if (actualRecursiveSize != size.sum!!.toLong()) {
            return ShouldContinue(true, recursiveEntryCount)
        }

        val actualRecursiveFiles = stats.getRecursiveFileCount(path.absolutePath)
        val actualRecursiveSubDirs = stats.getRecursiveDirectoryCount(path.absolutePath)
        if (recursiveSubDirs != actualRecursiveSubDirs) {
            return ShouldContinue(true, recursiveEntryCount)
        }

        if (recursiveFiles != actualRecursiveFiles) {
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
