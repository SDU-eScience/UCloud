package dk.sdu.cloud.indexing.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.events.EventProducer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.indexing.api.subscriptionStream
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.withLock
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SubscriptionService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: SubscriptionDao<Session>,
    private val eventStream: EventStreamService,
    private val lookupService: ReverseLookupService,
    private val distributedLockFactory: DistributedLockFactory
) {
    private val mutex = Mutex()
    private val producerCache = HashMap<String, EventProducer<StorageEvent>>()

    private suspend fun retrieveProducer(name: String): EventProducer<StorageEvent> {
        val existing = producerCache[name]
        if (existing != null) return existing

        mutex.withLock {
            val existingAfterLock = producerCache[name]
            if (existingAfterLock != null) return existingAfterLock

            val newProducer = eventStream.createProducer(subscriptionStream(name))
            producerCache[name] = newProducer
            return newProducer
        }
    }

    private fun subscriberLock(name: String): DistributedLock = distributedLockFactory.create("indexing-sublock-$name")

    suspend fun handleEvent(batch: List<StorageEvent>) {
        val eventsGroupedByFile = batch
            .filter { it.file?.fileIdOrNull != null }
            .groupBy { it.file!!.fileId }

        val fileIds = eventsGroupedByFile.keys

        val eventToSubscribers = db
            .withTransaction { dao.findSubscribers(it, fileIds) }
            .groupBy { it.subscriber }
            .mapValues { (_, subscriptions) ->
                subscriptions.flatMap { eventsGroupedByFile[it.fileId] ?: emptyList() }
            }

        coroutineScope {
            eventToSubscribers.map { (subscriber, events) ->
                launch {
                    subscriberLock(subscriber).withLock {
                        retrieveProducer(subscriber).produce(events)
                    }
                }
            }.joinAll()
        }

        val deletedFiles = batch.filterIsInstance<StorageEvent.Deleted>().mapNotNull { it.file?.fileIdOrNull }
        if (deletedFiles.isNotEmpty()) {
            db.withTransaction { session ->
                dao.deleteById(session, deletedFiles)
            }
        }
    }

    suspend fun addSubscription(principal: SecurityPrincipal, fileIds: Set<String>) {
        if (principal.role !in Roles.PRIVILEDGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        // Immediately acquire lock to ensure that no other events are sent to this subscriber. We do this to ensure
        // that the initial state is sent before any other updates are sent.
        subscriberLock(principal.username).withLock {
            // Setup the subscription to make sure that any events from this point are being collected (they cannot
            // be sent since we have the lock)
            db.withTransaction { session ->
                dao.addSubscription(session, principal.username, fileIds)
            }

            // Acquire the current state of files
            val fileIdsAsList = fileIds.toList()
            val events = lookupService.reverseLookupFileBatch(fileIdsAsList).mapIndexed { index, file ->
                val fileId = fileIdsAsList[index]

                if (file == null) {
                    // In case of no file we will emit that it has been deleted (even though it might not have).
                    // For the path and owner we pass dummy values which are unlikely to cause bad system behavior.
                    StorageEvent.Deleted(
                        StorageFile(FileType.FILE, "/unknown", fileId = fileId, ownerName = "_indexing"),
                        System.currentTimeMillis()
                    )
                } else {
                    StorageEvent.CreatedOrRefreshed(file, System.currentTimeMillis())
                }
            }

            retrieveProducer(principal.username).produce(events)
        }
    }

    suspend fun removeSubscription(principal: SecurityPrincipal, fileIds: Set<String>) {
        if (principal.role !in Roles.PRIVILEDGED) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        db.withTransaction { session ->
            dao.removeSubscriptions(session, principal.username, fileIds)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
