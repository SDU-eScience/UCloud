package dk.sdu.cloud.activity.processor

import com.fasterxml.jackson.databind.JsonNode
import dk.sdu.cloud.activity.api.ActivityEvent
import dk.sdu.cloud.activity.services.ActivityEventDao
import dk.sdu.cloud.client.defaultMapper
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

private typealias Transformer = (parsedEvent: JsonNode) -> List<ActivityEvent>?

class StorageAuditProcessor<DBSession>(
    private val streamFactory: EventConsumerFactory,
    private val db: DBSessionFactory<DBSession>,
    private val activityEventDao: ActivityEventDao<DBSession>,
    private val parallelism: Int = 4
) {
    private val transformers: List<Transformer> = listOf(
        this::transformBulkUpload,
        this::transformStat,
        this::transformAddFavorite,
        this::transformRemoveFavorite
    )

    fun init(): List<EventConsumer<*>> {
        return (0 until parallelism).map { _ ->
            streamFactory.createConsumer(FileDescriptions.auditStream).configure { root ->
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
                            activityEventDao.insertBatch(session, activityEvents)
                        }
                    }
            }
        }
    }

    private fun transformBulkUpload(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.bulkDownload.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            return it.request.fileIds.filterNotNull().map { fileId ->
                ActivityEvent.Download(username, System.currentTimeMillis(), fileId)
            }
        }

        return null
    }

    private fun transformStat(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.stat.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            val fileId = it.request.fileId ?: return null
            return listOf(ActivityEvent.Inspected(username, System.currentTimeMillis(), fileId))
        }

        return null
    }

    private fun transformAddFavorite(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.markAsFavorite.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            val fileId = it.request.fileId ?: return null
            return listOf(ActivityEvent.Favorite(username, true, System.currentTimeMillis(), fileId))
        }
        return null
    }

    private fun transformRemoveFavorite(parsedEvent: JsonNode): List<ActivityEvent>? {
        FileDescriptions.removeFavorite.parseAuditMessageOrNull(parsedEvent)?.let {
            val username = it.username ?: return null
            val fileId = it.request.fileId ?: return null
            return listOf(ActivityEvent.Favorite(username, false, System.currentTimeMillis(), fileId))
        }
        return null
    }

    private val AuditEvent<*>.username: String?
        get() = http.principal?.id

    companion object : Loggable {
        override val log = logger()
    }
}