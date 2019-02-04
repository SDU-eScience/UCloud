package dk.sdu.cloud.activity.processor

import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.service.AuditEvent
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.EventConsumerFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.StreamDescription
import dk.sdu.cloud.service.auditStream
import dk.sdu.cloud.service.batched
import dk.sdu.cloud.service.consumeBatchAndCommit
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.parseAuditMessageOrNull
import dk.sdu.cloud.service.stackTraceToString

private typealias Transformer = (parsedEvent: JsonNode) -> List<ActivityEvent>?

class StorageAuditProcessor<DBSession>(
    private val streamFactory: EventConsumerFactory,
    private val db: DBSessionFactory<DBSession>,
    private val activityService: ActivityService<DBSession>,
    private val parallelism: Int = 4
) {
    private val transformers: Map<StreamDescription<String, String>, List<Transformer>> = mapOf(
        FileDescriptions.auditStream to listOf(
            this::transformBulkDownload,
            this::transformStat,
            this::transformMove,
            this::transformDownload
        ),

        FileFavoriteDescriptions.auditStream to listOf(
            this::transformFavorite
        )
    )

    @Suppress("TooGenericExceptionCaught")
    fun init(): List<EventConsumer<*>> = (0 until parallelism).flatMap { _ ->
        transformers.map { (stream, transformers) ->
            streamFactory.createConsumer(stream).configure { root ->
                root
                    .batched(
                        batchTimeout = 500,
                        maxBatchSize = 1000
                    )
                    .consumeBatchAndCommit { batch ->
                        if (batch.isEmpty()) return@consumeBatchAndCommit

                        // Parse messages (and filter invalid ones)
                        val nodes = batch.mapNotNull {
                            try {
                                it.first to defaultMapper.readTree(it.second)
                            } catch (ex: Exception) {
                                log.debug("Caught exception while de-serializing event:")
                                log.debug("Event was: ${it.second}")
                                log.debug(ex.stackTraceToString())
                                null
                            }
                        }

                        // Collect events
                        //
                        // NOTE(Dan): Trying out some patterns. Event transformers should go in
                        // "transformers" at top of class
                        val activityEvents = ArrayList<ActivityEvent>()
                        for ((_, parsedEvent) in nodes) {
                            for (transformer in transformers) {
                                val transformed = transformer(parsedEvent)

                                if (transformed != null) {
                                    activityEvents.addAll(transformed)
                                    break
                                }
                            }
                        }

                        log.info("Received the following events: $activityEvents")

                        db.withTransaction { session ->
                            activityService.insertBatch(session, activityEvents)
                        }
                    }
            }
        }
    }

    private fun transformDownload(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.download.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            val path = it.request.request.path
            return it.request.fileIds.filterNotNull().map {
                ActivityEvent.Download(username, System.currentTimeMillis(), it, path)
            }
        }

        return null
    }

    private fun transformBulkDownload(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.bulkDownload.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            return it.request.fileIds.asSequence().filterNotNull().mapIndexed { index, fileId ->
                ActivityEvent.Download(
                    username,
                    System.currentTimeMillis(),
                    fileId,
                    it.request.fileIds.getOrNull(index) ?: "UNKNOWN_FILE"
                )
            }.toList()
        }

        return null
    }

    private fun transformStat(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.stat.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            val fileId = it.request.fileId ?: return null
            return listOf(
                ActivityEvent.Inspected(
                    username,
                    System.currentTimeMillis(),
                    fileId,
                    it.request.request.path
                )
            )
        }

        return null
    }

    private fun transformMove(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.move.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            val fileId = it.request.fileId ?: return null
            val newPath = it.request.request.newPath

            return listOf(
                ActivityEvent.Moved(
                    username,
                    newPath,
                    System.currentTimeMillis(),
                    fileId,
                    it.request.request.path
                )
            )
        }

        return null
    }

    private fun transformFavorite(parsedEvent: JsonNode): List<ActivityEvent>? {
        try {
            FileFavoriteDescriptions.toggleFavorite.parseAuditMessageOrNull(parsedEvent)?.let { audit ->
                val username = audit.username ?: return null

                return audit.request.files.mapNotNull {
                    val newStatus = it.newStatus
                    val fileId = it.fileId
                    if (newStatus == null || fileId == null) return@mapNotNull null

                    ActivityEvent.Favorite(
                        username,
                        newStatus,
                        System.currentTimeMillis(),
                        fileId,
                        it.path
                    )
                }
            }
        } catch (ex: Exception) {
            log.warn(ex.stackTraceToString())
        }

        return null
    }

    private val AuditEvent<*>.username: String?
        get() = http.token?.principal?.username

    companion object : Loggable {
        override val log = logger()
    }
}
