package dk.sdu.cloud.activity.processor

import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityService
import dk.sdu.cloud.calls.server.AuditEvent
import dk.sdu.cloud.calls.server.auditStream
import dk.sdu.cloud.calls.server.parseAuditMessageOrNull
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.BulkDownloadRequest
import dk.sdu.cloud.file.api.BulkFileAudit
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindByPath
import dk.sdu.cloud.file.api.MoveRequest
import dk.sdu.cloud.file.api.SingleFileAudit
import dk.sdu.cloud.file.favorite.api.FileFavoriteDescriptions
import dk.sdu.cloud.file.favorite.api.ToggleFavoriteAudit
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString

private typealias Transformer = (parsedEvent: JsonNode) -> List<ActivityEvent>?

class StorageAuditProcessor<DBSession>(
    private val streamFactory: EventStreamService,
    private val activityService: ActivityService<DBSession>
) {
    private val transformers: Map<EventStream<String>, List<Transformer>> = mapOf(
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
    fun init() {
        transformers.map { (stream, transformers) ->
            streamFactory.subscribe(stream, EventConsumer.Batched(
                maxBatchSize = 1000,
                maxLatency = 500L
            ) { batch ->
                if (batch.isEmpty()) return@Batched

                // Parse messages (and filter invalid ones)
                val nodes = batch.mapNotNull {
                    try {
                        defaultMapper.readTree(it)
                    } catch (ex: Exception) {
                        log.debug("Caught exception while de-serializing event:")
                        log.debug("Event was: $it")
                        log.debug(ex.stackTraceToString())
                        null
                    }
                }

                // Collect events
                //
                // NOTE(Dan): Trying out some patterns. Event transformers should go in
                // "transformers" at top of class
                val activityEvents = ArrayList<ActivityEvent>()
                for (parsedEvent in nodes) {
                    for (transformer in transformers) {
                        val transformed = transformer(parsedEvent)

                        if (transformed != null) {
                            activityEvents.addAll(transformed)
                            break
                        }
                    }
                }

                log.info("Received the following events: $activityEvents")

                activityService.insertBatch(activityEvents)
            })
        }
    }

    private fun transformDownload(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.download.parseAuditMessageOrNull<BulkFileAudit<FindByPath>>(parsedEvent)?.let {
            val username = it.username ?: return null
            val path = it.request.request.path
            return it.request.fileIds.filterNotNull().map {
                ActivityEvent.Download(username, System.currentTimeMillis(), it, path)
            }
        }

        return null
    }

    private fun transformBulkDownload(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.bulkDownload.parseAuditMessageOrNull<BulkFileAudit<BulkDownloadRequest>>(parsedEvent)?.let {
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
        FileDescriptions.stat.parseAuditMessageOrNull<SingleFileAudit<FindByPath>>(parsedEvent)?.let {
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
        FileDescriptions.move.parseAuditMessageOrNull<SingleFileAudit<MoveRequest>>(parsedEvent)?.let {
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
            FileFavoriteDescriptions.toggleFavorite.parseAuditMessageOrNull<ToggleFavoriteAudit>(parsedEvent)
                ?.let { audit ->
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
