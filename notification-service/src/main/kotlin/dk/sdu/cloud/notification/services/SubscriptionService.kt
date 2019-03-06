package dk.sdu.cloud.notification.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.withFixedHost
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.WSSession
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.notification.api.InternalNotificationRequest
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.notification.api.SubscriptionResponse
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.util.*

data class LocalSubscription(
    val username: String,
    val handler: CallHandler<*, SubscriptionResponse, *>,
    val id: Long
)

class SubscriptionService<Session>(
    private val localhost: HostInfo,
    private val wsServiceClient: AuthenticatedClient,
    private val db: DBSessionFactory<Session>,
    private val subscriptionDao: SubscriptionDao<Session>
) : Closeable {
    private var isRunning: Boolean = true

    init {
        localhost.port ?: throw NullPointerException("localhost.port == null")

        GlobalScope.launch {
            while (isActive && isRunning) {
                log.info("Cleaning up")
                db.withTransaction { subscriptionDao.refreshSessions(it, localhost.host, localhost.port!!) }
                delay(3000)
            }
        }
    }

    private val internalSessions = HashMap<WSSession, LocalSubscription>()
    private val internalSubscriptions = HashMap<Long, LocalSubscription>()
    private val lock = Mutex()

    override fun close() {
        isRunning = false
    }

    suspend fun onConnection(user: String, handler: CallHandler<*, SubscriptionResponse, *>) {
        db.withTransaction { session ->
            val id = subscriptionDao.open(session, user, localhost.host, localhost.port!!)
            lock.withLock {
                val localSubscription = LocalSubscription(user, handler, id)
                internalSubscriptions[id] = localSubscription
                internalSessions[(handler.ctx as WSCall).session] = localSubscription
            }
        }
    }

    suspend fun onDisconnect(session: WSSession) {
        val id = lock.withLock {
            val local = internalSessions[session]
            if (local != null) {
                internalSessions.remove(session)
                internalSubscriptions.remove(local.id)

            }

            local?.id
        }

        // It is safe to close it multiple times.
        if (id != null) db.withTransaction { subscriptionDao.close(it, id) }
    }

    suspend fun onNotification(user: String, notification: Notification, allowRemoteCalls: Boolean) {
        val subscriptions = db.withTransaction {
            subscriptionDao.findConnections(it, user)
        }

        // Deal with local subscriptions
        run {
            val invalidSubscriptions = ArrayList<Long>()
            subscriptions.filter { it.host == localhost }.forEach { subscription ->
                val localSubscription = internalSubscriptions[subscription.id]
                if (localSubscription == null) {
                    invalidSubscriptions.add(subscription.id)
                } else {
                    try {
                        localSubscription.handler.sendWSMessage(notification)
                    } catch (ex: Throwable) {
                        invalidSubscriptions.add(subscription.id)
                    }
                }

                Unit
            }

            if (invalidSubscriptions.isNotEmpty()) {
                db.withTransaction { session ->
                    invalidSubscriptions.forEach { id ->
                        log.debug("Closing dead session: $id")
                        subscriptionDao.close(session, id)
                    }
                }
            }
        }

        if (allowRemoteCalls) {
            // Deal with remote subscriptions
            run {
                GlobalScope.launch {
                    subscriptions
                        .filter { it.host != localhost }
                        .map { subscription ->
                            val client = wsServiceClient.withFixedHost(subscription.host)
                            launch {
                                NotificationDescriptions.internalNotification.call(
                                    InternalNotificationRequest(user, notification),
                                    client
                                )
                            }
                        }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()

        const val MAX_MS_SINCE_LAST_PING = 10_000
    }
}
