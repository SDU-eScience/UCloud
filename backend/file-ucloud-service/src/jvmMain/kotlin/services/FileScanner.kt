package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FileCollection
import dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags
import dk.sdu.cloud.file.orchestrator.api.FileCollectionsControl
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFile
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFileConstants
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import org.elasticsearch.ElasticsearchException
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryRequest
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class FileScanner(
    private val elastic: RestHighLevelClient,
    private val authenticatedService: AuthenticatedClient,
    private val db: DBContext,
    private val fs: NativeFS,
    private val pathConverter: PathConverter,
    private val fastDirectoryStats: CephFsFastDirectoryStats
) {

    suspend fun runScan() {
        //TODO
    }



    suspend fun runFullScan() {
        val scanTime = System.currentTimeMillis()

        val collections = fs.listFiles(pathConverter.relativeToInternal(RelativeInternalFile("/collections")))
        val home = fs.listFiles(pathConverter.relativeToInternal(RelativeInternalFile("/home")))
        val project = fs.listFiles(pathConverter.relativeToInternal(RelativeInternalFile("/projects")))

        val rootsAndCollections = mutableListOf<InternalFileAndCollection>()
        collections.chunked(50).forEach { chunk ->
            rootsAndCollections.addAll(
                solveChunk(
                    chunk,
                    providerGenerated = false,
                    collectionIdMapper = { it },
                    pathMapper = { RelativeInternalFile("/collections/$it") },
                )
            )
        }
        println(rootsAndCollections)

        home.chunked(50).forEach { chunk ->
            rootsAndCollections.addAll(
                solveChunk(
                    chunk,
                    providerGenerated = true,
                    collectionIdMapper = { PathConverter.COLLECTION_HOME_PREFIX + it },
                    pathMapper = { RelativeInternalFile("/home/$it") },
                )
            )
        }

        project.forEach {projectId ->
            fs.listFiles(pathConverter.relativeToInternal(RelativeInternalFile("/projects/$projectId")))
                .chunked(50)
                .forEach { rawChunk ->
                    val chunk = rawChunk.filter { it != PathConverter.PERSONAL_REPOSITORY }
                    rootsAndCollections.addAll(
                        solveChunk(
                            chunk,
                            providerGenerated = true,
                            collectionIdMapper = { PathConverter.COLLECTION_PROJECT_PREFIX + projectId + "/" + it },
                            pathMapper = { RelativeInternalFile("/projects/$projectId/$it") },
                        )
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
                rootsAndCollections.addAll(
                    solveChunk(
                        chunk,
                        providerGenerated = true,
                        collectionIdMapper = { PathConverter.COLLECTION_PROJECT_MEMBER_PREFIX + projectId + "/" + it },
                        pathMapper = {
                            RelativeInternalFile("/projects/$projectId/${PathConverter.PERSONAL_REPOSITORY}/$it")
                        }
                    )
                )
            }
        }

        val rootLock = Mutex()
        rootsAndCollections.sortBy { it.file.path }
        val roots = ArrayDeque(rootsAndCollections)

        (1..32).map {
            Thread {
                runBlocking {
                    val bulkRunner = BulkRequestBuilder()
                    while (isActive) {
                        val nextRoot = rootLock.withLock {
                            roots.removeFirstOrNull()
                        } ?: break

                        scan(bulkRunner, nextRoot, scanTime)
                    }

                    bulkRunner.flush()
                }
            }.also { it.start() }
        }.forEach {
            it.join()
        }

        println("Start deletion")
    //DELETE FILES THAT DO NOT EXISTS ANYMORE
        val queryDeleteRequest = DeleteByQueryRequest(FILES_INDEX)
        queryDeleteRequest.setConflicts("proceed")
        queryDeleteRequest.setQuery(
            QueryBuilders.rangeQuery(
                ElasticIndexedFileConstants.SCAN_TIME
            ).lt(scanTime)
        )
        queryDeleteRequest.batchSize = 100
        try {
            var moreToDelete = true
            while (moreToDelete) {
                try {
                    val response = elastic.deleteByQuery(queryDeleteRequest, RequestOptions.DEFAULT)
                    if (response.deleted == 0L) moreToDelete = false
                } catch (ex: SocketTimeoutException) {
                    log.warn(ex.message)
                    log.warn("Socket Timeout: Delay and try again")
                    delay(2000)
                }
            }
        } catch (ex: ElasticsearchException) {
            log.warn(ex.message)
            log.warn("Deletion failed")

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
        }
        println("Scan done")
    }

    private fun scan(bulkRunner: BulkRequestBuilder, root: InternalFileAndCollection, scanTime: Long) {
        println("Working in: ${root.file}")
        val queue = ArrayDeque<InternalFileAndCollection>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val nextItem = queue.removeFirst()

            //changed from update to insert without specific ID to increase throughput (Elastic does not have to check for ID already exists)
            // Insert the current file or folder into index
            var stat: NativeStat
            try {
                val insert = IndexRequest(FILES_INDEX).apply {
                    val beforestat = System.currentTimeMillis()
                    stat = fs.stat(nextItem.file)
                    val afterStat = System.currentTimeMillis()
                    bulkRunner.timespentOnStat += afterStat - beforestat
                    val writeValueAsBytes =
                        defaultMapper.encodeToString(
                            stat.toElasticIndexedFile(nextItem.file, nextItem.collection, scanTime)
                        )
                            .encodeToByteArray()
                    source(writeValueAsBytes, XContentType.JSON)
                }
                bulkRunner.add(insert)
            } catch (ex: FSException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    continue
                }
                else {
                    throw ex
                }
            }
            //Check if more files in case of dir and add to queue

            if(stat.fileType == FileType.DIRECTORY) {
                val fileList = kotlin.runCatching {
                    val timebeforelist = System.currentTimeMillis()
                    val list = fs.listFiles(nextItem.file).map {
                        InternalFileAndCollection(
                            InternalFile(nextItem.file.path + "/" + it),
                            nextItem.collection
                        )
                    }
                    val timeafterList = System.currentTimeMillis()
                    bulkRunner.timespentOnStorage += timeafterList - timebeforelist
                    list
                }.getOrNull()
                    ?: continue

                if (fileList.isEmpty() || fileList.any { it.file.fileName() == ".skipFolder" }) {
                    log.debug("Skipping ${nextItem.file.path} due to .skipFolder file or because the folder is empty")
                    continue
                }

                queue.addAll(fileList)
            }
        }
    }

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

    inner class BulkRequestBuilder {
        private var bulkCount = 0
        private var bulk = BulkRequest()

        private var docCount = 0L
        private var timespentInElastic = 0L
        private var startTime = System.currentTimeMillis()
        var timespentOnStorage = 0L
        var timespentOnStat = 0L
        val threadname = Thread.currentThread()

        fun flush() {
            if (bulkCount > 0) {
                val start = System.currentTimeMillis()
                val response = elastic.bulk(bulk, RequestOptions.DEFAULT)
                if (response.hasFailures()) {
                    response.items.forEach {
                        if (it.isFailed)
                            log.warn(it.failure.toString())
                    }
                }
                val end = System.currentTimeMillis()

                timespentInElastic += end-start
                docCount += bulkCount

                bulkCount = 0
                bulk = BulkRequest()
            }
        }

        fun getAndResetStatistics() {
            flush()
            val endTime = System.currentTimeMillis()
            println("$threadname Number of docs inserted: $docCount")
            println("$threadname Time spend in elastic: $timespentInElastic")
            println("$threadname Time spend in storage $timespentOnStorage")
            println("$threadname Time spend in stating $timespentOnStat")
            println("$threadname Time in total since last get and reset: ${endTime-startTime}")
            startTime = endTime
            docCount = 0
            timespentInElastic = 0
            timespentOnStorage = 0
            timespentOnStat = 0
        }

        private fun flushIfNeeded() {
            if (bulkCount >= 10000) {
                flush()
            }
        }

        fun add(indexRequest: IndexRequest) {
            bulkCount++
            bulk.add(indexRequest)
            flushIfNeeded()
        }

    }

    data class InternalFileAndCollection(
        val file: InternalFile,
        val collection: FileCollection
    )

    private suspend fun solveChunk(
        chunk: List<String>,
        providerGenerated: Boolean,
        collectionIdMapper: (id: String) -> String,
        pathMapper: (fileName: String) -> RelativeInternalFile,
    ): List<InternalFileAndCollection> {
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

        return mappedChunk.map { (path, coll) ->
            InternalFileAndCollection(
                pathConverter.relativeToInternal(pathMapper(path)),
                coll
            )
        }
    }

    private fun NativeStat.toElasticIndexedFile(
        file: InternalFile,
        collection: FileCollection,
        scanTime: Long?
    ): ElasticIndexedFile {
        return ElasticIndexedFile(
            path = pathConverter.internalToUCloud(file).path,
            size = size,
            fileType = fs.stat(file).fileType,
            createdAt = modifiedAt,
            owner = collection.owner.createdBy,
            projectId = collection.owner.project,
            scanTime = scanTime
        )
    }


    companion object : Loggable {
        override val log = logger()
        internal const val FILES_INDEX = "files"
    }
}
