package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.depth
import dk.sdu.cloud.file.ucloud.api.AnyOf
import dk.sdu.cloud.file.ucloud.api.Comparison
import dk.sdu.cloud.file.ucloud.api.ComparisonOperator
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFile
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFileConstants
import dk.sdu.cloud.file.ucloud.api.FileQuery
import dk.sdu.cloud.file.ucloud.api.NumericStatisticsRequest
import dk.sdu.cloud.file.ucloud.api.StatisticsRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import org.joda.time.LocalDateTime
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
    ): PageV2<FileCollection> {
        val includeFlags = if (providerGenerated) {
            FileCollectionIncludeFlags(filterProviderIds = collections.joinToString(","), includeOthers = includeOthers)
        } else {
            FileCollectionIncludeFlags(filterIds = collections.joinToString(","), includeOthers = includeOthers)
        }

        return FileCollectionsControl.browse.call(
            ResourceBrowseRequest(includeFlags, itemsPerPage = 250),
            authenticatedService
        ).orThrow()
    }

    //Loses microseconds from timestamp
    private fun rctimeToApproxLocalDateTime(rctime: String): LocalDateTime {
        return LocalDateTime(Date(rctime.split(".").first().toLong() * 1000))
    }

    private suspend fun submitScan(
        file: InternalFile,
        collection: FileCollection,
        upperLimitOfEntries: Long = Long.MAX_VALUE
    ) {
        val fileList = runCatching { fs.listFiles(file).map { InternalFile(file.path + "/" + it) } }.getOrNull()
            ?: return
        if (fileList.isEmpty() || fileList.any { it.fileName() == ".skipFolder" }) {
            log.debug("Skipping ${file.path} due to .skipFolder file or because the folder is empty")
            return
        }
        if (rctimeToApproxLocalDateTime(fastDirectoryStats.getRecursiveTime(file)) < lastScan) {
            log.debug("rcTime is older than lastscan = no change")
            return
        }

        val thisFileInIndex = run {
            val source =
                elastic.get(
                    GetRequest(FILES_INDEX, pathConverter.internalToUCloud(file).path),
                    RequestOptions.DEFAULT
                )?.sourceAsString
            if (source != null) {
                defaultMapper.decodeFromString<ElasticIndexedFile>(source)
            } else {
                null
            }
        }

        var newUpperLimitOfEntries = 1L
        try {
            if (fs.stat(file).fileType == FileType.DIRECTORY) {
                val rctime = runCatching { fastDirectoryStats.getRecursiveTime(file) }.getOrNull()
                val (shouldContinue, limit) = shouldContinue(file, upperLimitOfEntries)
                newUpperLimitOfEntries = limit
                if (rctime != null && thisFileInIndex != null && thisFileInIndex.rctime == rctime && !shouldContinue) {
                    log.debug("${file.path} already up-to-date")
                    return
                }
            }
        } catch (ex: FSException) {
            log.debug(ex.stackTraceToString())
            return
        }

        val filesInDir = runCatching {
            fileList.map {
                fs.stat(it).toElasticIndexedFile(it, collection)
            }.associateBy { it.path }
        }.getOrElse { emptyMap() }

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
            val stat = runCatching { fs.stat(f) }.getOrNull() ?: return@mapNotNull null
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
        if (actualRecursiveSize == null) {
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

    private fun NativeStat.toElasticIndexedFile(
        file: InternalFile,
        collection: FileCollection
    ): ElasticIndexedFile {
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

    suspend fun runScan() {
        val collectionRoot = pathConverter.relativeToInternal(RelativeInternalFile("/collections"))
        val homeRoot = pathConverter.relativeToInternal(RelativeInternalFile("/home"))
        val projectsRoot = pathConverter.relativeToInternal(RelativeInternalFile("/projects"))
        val collections = fs.listFiles(collectionRoot)
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
                ?: LocalDateTime().withDate(1970, 1, 1)
        }

        println("Scanning Collections")
        collections.chunked(50).forEach { chunk ->
            submitChunk(
                chunk,
                providerGenerated = false,
                collectionIdMapper = { it },
                pathMapper = { RelativeInternalFile("/collections/$it") }
            )
        }

        println("Scanning home folders")
        home.chunked(50).forEach { chunk ->
            submitChunk(
                chunk,
                providerGenerated = true,
                collectionIdMapper = { PathConverter.COLLECTION_HOME_PREFIX + it },
                pathMapper = { RelativeInternalFile("/home/$it") }
            )
        }

        println("Scanning Project folders")
        project.forEach { projectId ->
            fs.listFiles(pathConverter.relativeToInternal(RelativeInternalFile("/projects/$projectId")))
                .chunked(50)
                .forEach { rawChunk ->
                    val chunk = rawChunk.filter { it != PathConverter.PERSONAL_REPOSITORY }
                    submitChunk(
                        chunk,
                        providerGenerated = true,
                        collectionIdMapper = { PathConverter.COLLECTION_PROJECT_PREFIX + projectId + "/" + it },
                        pathMapper = { RelativeInternalFile("/projects/$projectId/$it") }
                    )
                }

            val memberFiles = try {
                fs.listFiles(
                    pathConverter.relativeToInternal(
                        RelativeInternalFile("/projects/$projectId/${PathConverter.PERSONAL_REPOSITORY}")
                    )
                )
            } catch (ex: FSException.NotFound) {
                // Ignore
                null
            }

            memberFiles?.chunked(50)?.forEach { chunk ->
                submitChunk(
                    chunk,
                    providerGenerated = true,
                    collectionIdMapper = { PathConverter.COLLECTION_PROJECT_MEMBER_PREFIX + projectId + "/" + it },
                    pathMapper = {
                        RelativeInternalFile("/projects/$projectId/${PathConverter.PERSONAL_REPOSITORY}/$it")
                    }
                )
            }
        }

        db.withSession { session ->
            val hasExisting = session.sendPreparedStatement(
                """
                    update file_ucloud.storage_scan set
                    last_system_scan = now()
                    where true
                """
            ).rowsAffected == 0L

            if (!hasExisting) {
                session.sendPreparedStatement(
                    """
                        insert into file_ucloud.storage_scan
                        values (now())
                    """.trimIndent()
                )
            }

            println("Scan done")
        }
    }

    private suspend fun submitChunk(
        chunk: List<String>,
        providerGenerated: Boolean,
        collectionIdMapper: (id: String) -> String,
        pathMapper: (fileName: String) -> RelativeInternalFile,
    ) {
        val chunkIdToColl = chunk.asSequence().map { it to collectionIdMapper(it) }.toMap()
        val resolvedCollections = retrieveCollections(
            providerGenerated,
            includeOthers = true,
            chunkIdToColl.values.toList()
        )

        val mappedChunk = chunk.mapNotNull { collId ->
            val resolvedCollId = chunkIdToColl[collId] ?: return@mapNotNull run {
                log.warn("Skipping $collId")
                null
            }

            val mappedCollection = resolvedCollections.items.find {
                if (providerGenerated) {
                    resolvedCollId == it.providerGeneratedId
                } else {
                    resolvedCollId == it.id
                }
            }

            if (mappedCollection == null) {
                log.warn("Skipping $collId")
                return@mapNotNull null
            }

            collId to mappedCollection
        }

        mappedChunk.forEach { (path, coll) ->
            withContext(pool) {
                launch {
                    val file =
                        pathConverter.relativeToInternal(pathMapper(path))
                    submitScan(file, coll)
                }.join()
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        internal const val FILES_INDEX = "files"
    }
}
