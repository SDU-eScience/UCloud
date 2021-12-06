package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.*
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.ucloud.api.*
import dk.sdu.cloud.file.ucloud.services.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkRequestBuilder
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.joda.time.DateTime
import org.joda.time.LocalDateTime
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.abs

class FileScanner(
    private val elastic: RestHighLevelClient,
    private val authenticatedService: AuthenticatedClient,
    private val db: DBContext,
    private val fs: NativeFS,
    private val pathConverter: PathConverter,
    private val fastDirectoryStats: CephFsFastDirectoryStats,
    private val query: ElasticQueryService,
    ) {
    private val pool = Executors.newFixedThreadPool(16).asCoroutineDispatcher()
    private lateinit var lastScan: LocalDateTime

    private suspend fun retrieveCollections(
        providerGenerated: Boolean,
        includeOthers: Boolean,
        collections: List<String>,
        next: String?
    ): PageV2<FileCollection> {
        val includeFlags = if (providerGenerated) {
            FileCollectionIncludeFlags(filterProviderIds = collections.joinToString(","), includeOthers = includeOthers)
        } else {
            FileCollectionIncludeFlags(filterIds = collections.joinToString(","), includeOthers = includeOthers)
        }

        return FileCollectionsControl.browse.call(
            ResourceBrowseRequest(includeFlags, next = next),
            authenticatedService
        ).orThrow()

    }

    suspend fun runScan() {
        val collectionRoot = pathConverter.relativeToInternal(RelativeInternalFile("/collections"))
        val homeRoot = pathConverter.relativeToInternal(RelativeInternalFile("/home"))
        val projectsRoot = pathConverter.relativeToInternal(RelativeInternalFile("/projects"))
        val collections = fs.listFiles(collectionRoot).mapNotNull { it.toLongOrNull() }
        val home = fs.listFiles(homeRoot)
        val project = fs.listFiles(projectsRoot)

        db.withSession { session ->
            lastScan = session.sendPreparedStatement(
                """
                    SELECT last_system_scan
                    from file_ucloud.storage_scan
                """
            ).rows
                .firstOrNull()
                ?.getDate(0)
                ?: LocalDateTime().withDate(1970,1,1)

            println("Scanning Collections")
            collections.chunked(50).forEach { chunk ->
                var resolvedCollections =
                    retrieveCollections(providerGenerated = false, includeOthers = true, chunk.map { it.toString() }, next = null)
                val collectionsList = resolvedCollections.items.sortedBy { it.owner.createdBy }
                //Filtering might only be necessary on Dev (inconsistent DB vs file system (multiple users of dev))
                val filteredChunk = if (collectionsList.size != chunk.size) {
                    log.warn("Might be missing collection. Number of collections: ${collectionsList.size}. Chunk size: ${chunk.size}")
                    val ids = collectionsList.map { it.id.toLong() }
                    chunk.mapNotNull { collectionID ->
                        if (ids.contains(collectionID)) {
                            collectionID
                        } else {
                            log.warn("Skipping $collectionID")
                            null
                        }
                    }
                } else chunk

                while (true) {
                    collectionsList.zip(filteredChunk).forEach { (coll, path) ->
                        withContext(pool) {
                            launch {
                                val file =
                                    pathConverter.relativeToInternal(RelativeInternalFile("/collections/${path}"))
                                submitScan(file, coll)
                            }.join()
                        }
                    }

                    if (resolvedCollections.next == null) {
                        return@forEach
                    }
                    resolvedCollections = retrieveCollections(
                        providerGenerated = false,
                        includeOthers = true,
                        chunk.map { it.toString() },
                        next = resolvedCollections.next
                    )
                }
            }

            println("Scanning home folders")
            home.chunked(50).forEach { chunk ->
                var resolvedCollections = retrieveCollections(
                    providerGenerated = true,
                    includeOthers = true,
                    chunk.map { PathConverter.COLLECTION_HOME_PREFIX + it },
                    next = null
                )
                val collectionsList = resolvedCollections.items.sortedBy { it.owner.createdBy }
                val filteredChunk = if (collectionsList.size != chunk.size) {
                    log.warn("Might be missing collection. Number of collections: ${collectionsList.size}. Chunk size: ${chunk.size}")
                    val usersFolders = collectionsList.map { it.owner.createdBy }
                    chunk.mapNotNull { username ->
                        if (usersFolders.contains(username)) {
                            username
                        }
                        else {
                            log.warn("Skipping $username")
                            null
                        }
                    }
                } else chunk

                while (true) {
                    collectionsList.zip(filteredChunk.sortedBy { it }).forEach { (coll, path) ->
                        withContext(pool) {
                            launch {
                                val file =
                                    pathConverter.relativeToInternal(RelativeInternalFile("/home/$path"))
                                submitScan(file, coll)
                            }.join()
                        }
                    }

                    if (resolvedCollections.next == null) {
                        return@forEach
                    }
                    resolvedCollections = retrieveCollections(
                        providerGenerated = true,
                        includeOthers = true,
                        chunk.map { PathConverter.COLLECTION_HOME_PREFIX + it },
                        resolvedCollections.next
                    )
                }
            }

            println("Scanning Project folders")
            project.chunked(50).forEach { chunk ->
                var resolvedCollections = retrieveCollections(
                    providerGenerated = true,
                    includeOthers = true,
                    chunk.map { PathConverter.COLLECTION_PROJECT_PREFIX + it },
                    next = null
                )
                val collectionsList = resolvedCollections.items.sortedBy { it.owner.createdBy }
                val filteredChunk = if (collectionsList.size != chunk.size) {
                    log.warn("Might be missing collection. Number of collections: ${collectionsList.size}. Chunk size: ${chunk.size}")

                    val projectIds = collectionsList.map{ it.owner.project }
                    chunk.mapNotNull { projectFolder ->
                        if (projectIds.contains(projectFolder)) {
                            projectFolder
                        } else {
                            log.warn("Skipping $projectFolder")
                            null
                        }
                    }

                } else chunk

                while (true) {
                    collectionsList.zip(filteredChunk).forEach { (coll, path) ->
                        withContext(pool) {
                            launch {
                                val file =
                                    pathConverter.relativeToInternal(RelativeInternalFile("/projects/$path}"))
                                submitScan(file, coll)
                            }.join()
                        }
                    }

                    if (resolvedCollections.next == null) {
                        return@forEach
                    }
                    resolvedCollections = retrieveCollections(
                        providerGenerated = true,
                        includeOthers = true,
                        chunk.map { PathConverter.COLLECTION_PROJECT_PREFIX + it },
                        resolvedCollections.next
                    )
                }
            }

            if (session.sendPreparedStatement(
                """
                    UPDATE file_ucloud.storage_scan set
                    last_system_scan = now()
                    where true
                """
            ).rowsAffected == 0L ) {
                session.sendPreparedStatement(
                    """
                        INSERT into file_ucloud.storage_scan
                        values (now())
                    """.trimIndent()
                )
            }
            println("Scan done")
        }
    }

    //Loses microseconds from timestamp
    private fun rctimeToApproxLocalDateTime(rctime: String): LocalDateTime {
        return LocalDateTime(Date(rctime.split(".").first().toLong()*1000))
    }

    private suspend fun submitScan(file: InternalFile, collection: FileCollection, upperLimitOfEntries: Long = Long.MAX_VALUE) {
        val fileList = fs.listFiles(file).map { InternalFile(file.path + "/" + it) }
        if (fileList.isEmpty() || fileList.any { it.fileName() == ".skipFolder" }) {
            log.debug("Skipping ${file.path} due to .skipFolder file or because the folder is empty")
            return
        }
        if (rctimeToApproxLocalDateTime(fastDirectoryStats.getRecursiveTime(file)) < lastScan ) {
            log.debug("rcTime is older than lastscan = no change")
            return
        }

        val thisFileInIndex = run {
            val source =
                elastic.get(GetRequest(FILES_INDEX, pathConverter.internalToUCloud(file).path), RequestOptions.DEFAULT)?.sourceAsString
            if (source != null) {
                defaultMapper.decodeFromString<ElasticIndexedFile>(source)
            } else {
                null
            }
        }

        var newUpperLimitOfEntries = 1L
        if (fs.stat(file).fileType == FileType.DIRECTORY) {
            val rctime = runCatching { fastDirectoryStats.getRecursiveTime(file) }.getOrNull()
            val (shouldContinue, limit) = shouldContinue(file, upperLimitOfEntries)
            newUpperLimitOfEntries = limit
            if (rctime != null && thisFileInIndex != null && thisFileInIndex.rctime == rctime && !shouldContinue) {
                log.debug("${file.path} already up-to-date")
                return
            }
        }
        val filesInDir = fileList.map {
            fs.stat(it).toElasticIndexedFile(it,collection)
        }.associateBy { it.path }

        val relativeFilePath = pathConverter.internalToUCloud(InternalFile(file.path)).path

        val filesInIndex = query.queryForScan(
            FileQuery(
                listOf(relativeFilePath),
                fileDepth = AnyOf.with(
                    Comparison(relativeFilePath.depth() + 1, ComparisonOperator.EQUALS)
                )
            )
        ).associateBy { it.path }

        val bulk = BulkRequestBuilder()

        //Delete files not in folder anymore and their sub files/directories
        filesInIndex.values.asSequence()
            .filter { it.path !in filesInDir }
            .forEach {
                bulk.add(DeleteRequest(FILES_INDEX, it.path))
                val queryDeleteRequest = DeleteByQueryRequest(FILES_INDEX)
                queryDeleteRequest.setConflicts("proceed")
                queryDeleteRequest.setQuery(
                    QueryBuilders.wildcardQuery(
                        ElasticIndexedFileConstants.PATH_FIELD,
                        "${it.path}/*"
                    )
                )
                queryDeleteRequest.batchSize = 100
                try {
                    var moreToDelete = true
                    while (moreToDelete) {
                        val response = elastic.deleteByQuery(queryDeleteRequest, RequestOptions.DEFAULT)
                        if (response.deleted == 0L) moreToDelete = false
                    }
                } catch (ex: ElasticsearchException) {
                    log.warn(ex.message)
                    log.warn("Deletion of ${it.path}/* , failed")
                }
            }

        filesInDir.values.asSequence()
            .filter { it.path !in filesInIndex }
            .forEach {
                bulk.add(
                    UpdateRequest(FILES_INDEX, it.path).apply {
                        val writeValueAsBytes = defaultMapper.encodeToString(it).encodeToByteArray()
                        doc(writeValueAsBytes, XContentType.JSON)
                        docAsUpsert(true)
                    }
                )
            }

        if (thisFileInIndex == null) {
            val update = UpdateRequest(FILES_INDEX, relativeFilePath).apply {
                val writeValueAsBytes =
                    defaultMapper.encodeToString(fs.stat(file).toElasticIndexedFile(file, collection))
                        .encodeToByteArray()
                doc(writeValueAsBytes, XContentType.JSON)
                docAsUpsert(true)
            }
            bulk.add(update)
        }

        bulk.flush()

        fileList.mapNotNull { f ->
            val stat = fs.stat(f)
            withContext(pool) {
                if (stat.fileType == FileType.DIRECTORY) {
                    launch {
                        submitScan(
                            InternalFile(f.path),
                            collection,
                            newUpperLimitOfEntries
                        )
                    }
                } else {
                    null
                }
            }
        }.joinAll()
    }

    data class ShouldContinue(val shouldContinue: Boolean, val newUpperLimitOfEntries: Long)

    private fun shouldContinue(internalFile: InternalFile, upperLimitOfEntries: Long): ShouldContinue {
        val file = fs.stat(internalFile)
        if (file.fileType == FileType.FILE) return ShouldContinue(true, 1)
        if (upperLimitOfEntries < 100) return ShouldContinue(true, upperLimitOfEntries)
        val recursiveEntryCount = fastDirectoryStats.getRecursiveEntryCount(internalFile)
        if (recursiveEntryCount > upperLimitOfEntries) return ShouldContinue(true, recursiveEntryCount)

        val fileStats = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(listOf(internalFile.path)),
                size = NumericStatisticsRequest(calculateSum = true)
            )
        )

        val fileCount = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(
                    listOf(internalFile.path),
                    fileTypes = AnyOf.with(FileType.FILE)
                )
            )
        )

        val dirCount = query.statisticsQuery(
            StatisticsRequest(
                FileQuery(
                    listOf(internalFile.path),
                    fileTypes = AnyOf.with(FileType.DIRECTORY)
                )
            )
        )

        val size = fileStats.size!!
        val recursiveFiles = fileCount.count
        val recursiveSubDirs = dirCount.count

        if (recursiveEntryCount != recursiveFiles + recursiveSubDirs) {
            log.debug("Entry count is different ($recursiveEntryCount != $recursiveFiles + $recursiveSubDirs)")
            return ShouldContinue(true, recursiveEntryCount)
        }

        val actualRecursiveSize = fastDirectoryStats.getRecursiveSize(internalFile)
        if (actualRecursiveSize == null ) {
            log.debug("Did not get size - not testing and cannot find ceph commands")
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
        }

        val sum = size.sum
        if (sum!!.toLong() != actualRecursiveSize) {
            val percentage = if (sum.toLong() == 0L) {
                1.0
            } else {
                1 - (actualRecursiveSize / sum)
            }

            if (percentage >= abs(0.05)) {
                log.debug("Size is different $actualRecursiveSize != $sum")
                return ShouldContinue(true, recursiveEntryCount)
            }
        }
        val actualRecursiveFiles = fastDirectoryStats.getRecursiveDirectoryCount(internalFile)
        val actualRecursiveSubDirs = fastDirectoryStats.getRecursiveDirectoryCount(internalFile)
        if (recursiveSubDirs != actualRecursiveSubDirs) {
            log.debug("Sub dirs is different ${recursiveSubDirs} ${actualRecursiveSubDirs}")
            return ShouldContinue(true, recursiveEntryCount)
        }

        if (recursiveFiles != actualRecursiveFiles) {
            log.debug("Recursive files is different $recursiveFiles $actualRecursiveFiles")
            return ShouldContinue(true, recursiveEntryCount)
        }

        log.debug("Skipping ${internalFile.path} ($recursiveEntryCount entries has been skipped)")

        return ShouldContinue(false, recursiveEntryCount)
    }

    private fun NativeStat.toElasticIndexedFile(file: InternalFile, collection: FileCollection): ElasticIndexedFile {
        return ElasticIndexedFile(
            path = pathConverter.internalToUCloud(file).path,
            size = size,
            fileType = fs.stat(file).fileType,
            rctime = runCatching {
                fastDirectoryStats.getRecursiveTime(file)
            }.getOrNull(),
            permission = collection.permissions?.others?.first()?.permissions ?: emptyList(),
            createdAt = modifiedAt,
            collectionId = collection.id,
            owner = collection.owner.createdBy,
            projectId = collection.owner.project
        )
    }

    inner class BulkRequestBuilder {
        private var bulkCount = 0
        private var bulk = BulkRequest()

        fun flush() {
            if (bulkCount > 0) {
                val response = elastic.bulk(bulk, RequestOptions.DEFAULT)
                if (response.hasFailures()) {
                    response.items.forEach {
                        if (it.isFailed)
                            log.warn(it.failure.toString())
                    }
                }
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

    companion object : Loggable {
        override val log = logger()
        internal const val FILES_INDEX = "files"
    }
}
