package dk.sdu.cloud.file.ucloud.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Conflicts
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch.core.BulkRequest
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.core.bulk.CreateOperation
import co.elastic.clients.json.JsonData
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
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.ArrayDeque

class FileScanner(
    private val elastic: ElasticsearchClient,
    private val authenticatedService: AuthenticatedClient,
    private val db: DBContext,
    private val fs: NativeFS,
    private val pathConverter: PathConverter,
    private val fastDirectoryStats: CephFsFastDirectoryStats
) {

    private fun rctimeToApproxLocalDateTime(rctime: String): LocalDateTime {
        return LocalDateTime.ofInstant(Date(rctime.split(".").first().toLong() * 1000).toInstant(), ZoneId.from(ZoneOffset.UTC))
    }

    suspend fun runFastScan() {
        val scanTime = System.currentTimeMillis()
        val rootLock = Mutex()
        val roots = getAllRoots()

        val lastScan = db.withSession { session ->
            session.sendPreparedStatement(
                """
                    SELECT * 
                    FROM file_ucloud.storage_scan
                """
            ).rows
                .singleOrNull()
                ?.getDate(0)
                ?: LocalDateTime.ofInstant(Date(System.currentTimeMillis() - (1000L * 60 * 60 * 24)).toInstant(), ZoneId.from(ZoneOffset.UTC))
        }

        val activeRoots = roots.mapNotNull {
            if (rctimeToApproxLocalDateTime(fastDirectoryStats.getRecursiveTime(it.file)) >= lastScan) {
                return@mapNotNull it
            }
            null
        }
        val activeRootsQueue = ArrayDeque(activeRoots)

        //println("filtered ${activeRootsQueue.size} : orig ${roots.size}")
        (1..32).map {
            Thread {
                runBlocking {
                    val bulkRunner = BulkRequestBuilder()
                    while (isActive) {
                        val nextRoot = rootLock.withLock {
                            activeRootsQueue.removeFirstOrNull()
                        } ?: break

                        fastScan(bulkRunner, nextRoot, scanTime, lastScan)
                    }

                    bulkRunner.flush()
                   /* println("${bulkRunner.threadname} Total amount of docs ${bulkRunner.totalDocCount}")
                    println("${bulkRunner.threadname} Folders skipped in this run ${bulkRunner.foldersSkipped}" )
                    println("${bulkRunner.threadname} Files skipped in this run ${bulkRunner.filesSkipped}" )*/
                }
            }.also { it.start() }
        }.forEach {
            it.join()
        }

        updateScanTime()
        println("Scan done")
    }

    private suspend fun fastScan(bulkRunner: BulkRequestBuilder, root: InternalFileAndCollection, scanTime: Long, lastScan: LocalDateTime) {
        println("Working in: ${root.file}")
        val queue = ArrayDeque<InternalFileAndCollection>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val nextItem = queue.removeFirst()

            //changed from update to insert without specific ID to increase throughput (Elastic does not have to check for ID already exists)
            // Insert the current file or folder into index
            var stat: NativeStat
            try {
                val beforestat = System.currentTimeMillis()
                stat = fs.stat(nextItem.file)
                val afterStat = System.currentTimeMillis()
                bulkRunner.timespentOnStat += afterStat - beforestat
                val insert = if (LocalDateTime.ofInstant(Date(stat.modifiedAt).toInstant(), ZoneId.from(ZoneOffset.UTC)) > lastScan) {
                    CreateOperation.Builder<ElasticIndexedFile>()
                        .index(FILES_INDEX)
                        .document(stat.toElasticIndexedFile(nextItem.file, nextItem.collection, scanTime))
                        .build()
                    } else {
                        bulkRunner.filesSkipped++
                        null
                    }
                if (insert != null) {
                    bulkRunner.add(insert)
                }
            } catch (ex: FSException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    continue
                }
                else {
                    throw ex
                }
            }
            //Check if more files in case of dir and add to queue ONLY if changes were made to folder since last scan

            if(stat.fileType == FileType.DIRECTORY) {
                if (rctimeToApproxLocalDateTime(fastDirectoryStats.getRecursiveTime(nextItem.file)) >= lastScan) {
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
                else {
                    bulkRunner.foldersSkipped++
                }
            }
        }
    }

    suspend fun runFullScan() {
        val scanTime = System.currentTimeMillis()
        val rootLock = Mutex()
        val roots = getAllRoots()

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
        val queryDeleteRequest = DeleteByQueryRequest.Builder()
            .index(FILES_INDEX)
            .conflicts(Conflicts.Proceed)
            .query(
                RangeQuery.Builder()
                    .field(ElasticIndexedFileConstants.SCAN_TIME)
                    .lt(JsonData.of(scanTime))
                    .build()._toQuery()
            )
            .build()
        try {
            var moreToDelete = true
            while (moreToDelete) {
                try {
                    val response = elastic.deleteByQuery(queryDeleteRequest)
                    if (response.deleted() == 0L) moreToDelete = false
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

        updateScanTime()
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
                val insert = CreateOperation.Builder<ElasticIndexedFile>().index(FILES_INDEX).apply {
                    val beforestat = System.currentTimeMillis()
                    stat = fs.stat(nextItem.file)
                    val afterStat = System.currentTimeMillis()
                    bulkRunner.timespentOnStat += afterStat - beforestat

                    this.document(stat.toElasticIndexedFile(nextItem.file, nextItem.collection, scanTime))

                }
                bulkRunner.add(insert.build())
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


    private suspend fun updateScanTime() {
        db.withSession { session ->
            val hasExisting = session.sendPreparedStatement(
                """
                    update file_ucloud.storage_scan set
                    last_system_scan = now()
                    where true
                """
            ).rowsAffected != 0L
            if (!hasExisting) {
                session.sendPreparedStatement(
                    """
                        insert into file_ucloud.storage_scan
                        values (now())
                    """.trimIndent()
                )
            }
        }
    }

    private suspend fun getAllRoots(): ArrayDeque<InternalFileAndCollection> {
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

        rootsAndCollections.sortBy { it.file.path }
        return ArrayDeque(rootsAndCollections)
    }

    inner class BulkRequestBuilder {
        private var bulkCount = 0
        private var bulk = BulkRequest.Builder()

        private var docCount = 0L
        private var timespentInElastic = 0L
        private var startTime = System.currentTimeMillis()
        var timespentOnStorage = 0L
        var timespentOnStat = 0L
        val threadname = Thread.currentThread()
        var totalDocCount = 0L
        var foldersSkipped = 0L
        var filesSkipped = 0L
        private var operations = mutableListOf<BulkOperation>()

        fun flush() {
            if (bulkCount > 0) {
                val start = System.currentTimeMillis()
                val response = elastic.bulk(bulk.build())
                if (response.errors()) {
                    response.items().forEach {
                        if (it.error() != null)
                            log.warn(it.error().toString())
                    }
                }
                val end = System.currentTimeMillis()

                timespentInElastic += end-start
                docCount += bulkCount
                totalDocCount += bulkCount
                bulkCount = 0
                bulk = BulkRequest.Builder()
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
            operations = mutableListOf<BulkOperation>()
        }

        private fun flushIfNeeded() {
            if (bulkCount >= 10000) {
                flush()
            }
        }

        fun add(createOperationRequest: CreateOperation<ElasticIndexedFile>) {
            bulkCount++
            operations.add(BulkOperation.Builder().create(createOperationRequest).build())
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
