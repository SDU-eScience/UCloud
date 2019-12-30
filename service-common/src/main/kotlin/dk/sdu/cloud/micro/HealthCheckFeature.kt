package dk.sdu.cloud.micro

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.events.RedisScope
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.client.RequestOptions
import java.util.*

private object HealthCheckDescriptions : CallDescriptionContainer("healthcheck") {
    const val baseContext = "/status"

    val status = call<Unit, Unit, CommonErrorMessage>("status") {
        auth {
            access = AccessRight.READ
            roles = Roles.PUBLIC
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
            }
        }
    }
}

private data class RedisHealthMessage(val id: String, val timestamp: Long)

private class RedisHealthStream(serviceName: String, id: Int) : EventStreamContainer() {
    val health = stream<RedisHealthMessage>("$serviceName-$id-rhealth", { it.id })
}

class HealthCheckFeature : MicroFeature {
    private var lastObservedRedisMessage = System.currentTimeMillis()

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val serverFeature = ctx.featureOrNull(ServerFeature) ?: return
        val redisFeature = ctx.featureOrNull(RedisFeature)
        val hibernateFeature = ctx.featureOrNull(HibernateFeature)
        val elasticFeature = ctx.featureOrNull(ElasticFeature)

        val isObservingRedisStreams = if (redisFeature != null) {
            initializeRedisHealthStreams(ctx)
        } else {
            false
        }

        with(serverFeature.server) {
            implement(HealthCheckDescriptions.status) {
                if (redisFeature != null) {
                    log.debug("Testing Redis")
                    val response: String
                    try {
                        val conn = ctx.redisConnectionManager.getConnection()
                        response = conn.ping().get()
                    } catch (ex: Exception) {
                        log.error("Redis is not working: EX: ${ex.stackTraceToString()}")
                        throw RPCException(
                            "Redis is not working: EX: ${ex.stackTraceToString()}",
                            HttpStatusCode.InternalServerError
                        )
                    }
                    if (response != "PONG") {
                        throw RPCException("Redis is not working", HttpStatusCode.InternalServerError)
                    }

                    if (isObservingRedisStreams) {
                        val now = System.currentTimeMillis()
                        val timeSinceLastMessage = now - lastObservedRedisMessage
                        if (timeSinceLastMessage >
                            REDIS_HEALTH_STREAM_MESSAGE_MAX_AGE * REDIS_HEALTH_MAX_MISSED_MESSAGES
                        ) {
                            log.error(
                                "Redis consumer/producer has stopped working. " +
                                        "Time since last message: $timeSinceLastMessage"
                            )

                            throw RPCException(
                                "Redis consumer/producer has stopped working",
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                }

                if (hibernateFeature != null) {
                    log.debug("Testing Hibernate")
                    val result = try {
                        ctx.hibernateDatabase.withTransaction { session ->
                            session.createNativeQuery(
                                "SELECT 1"
                            ).resultList
                        }
                    } catch (ex: Exception) {
                        log.error("Hibernate is not working: EX: ${ex.stackTraceToString()}")
                        throw RPCException(
                            "Hibernate is not working, EX: ${ex.stackTraceToString()}",
                            HttpStatusCode.InternalServerError
                        )
                    }
                    if (result.isEmpty()) {
                        throw RPCException("Hibernate is not working", HttpStatusCode.InternalServerError)
                    }
                }

                if (elasticFeature != null) {
                    log.debug("Testing Elastic")
                    val client = ctx.elasticHighLevelClient
                    val request = ClusterHealthRequest()
                    try {
                        val response = client.cluster().health(request, RequestOptions.DEFAULT)
                    } catch (ex: Exception) {
                        log.error("Elastic is not working ${ex.stackTraceToString()}")
                        throw RPCException("Elastic is not working", HttpStatusCode.InternalServerError)
                    }
                }
                ok(Unit)
            }
        }
    }

    private fun initializeRedisHealthStreams(ctx: Micro): Boolean {
        ctx.featureOrNull(RedisFeature) ?: return false

        val serviceName = ctx.serviceDescription.name
        val lockFactory = DistributedLockBestEffortFactory(ctx)

        val uuid = UUID.randomUUID().toString()
        var id: Int = -1
        var lock: DistributedLock? = null
        runBlocking {
            // Note: This puts a cap of $MAX_REDIS_LOCKS concurrently running instances of a service in
            // the same cluster.
            for (i in 0..MAX_REDIS_LOCKS) {
                val attemptedLock = lockFactory.create(
                    "$serviceName-$i-rhealthlkck",
                    // Allow us to miss a few events before lock is released
                    REDIS_HEALTH_STREAM_PAUSE_BETWEEN_EVENTS * 3
                )
                if (attemptedLock.acquire()) {
                    id = i
                    lock = attemptedLock
                    break
                }
            }

            id
        }

        if (id == -1 || lock == null) {
            log.warn(
                "Could not find available Redis health check stream! There is a limit of " +
                        "$MAX_REDIS_LOCKS concurrent services. Are streams not being released correctly?"
            )
            return false
        }

        log.debug("Using stream with ID: $id")

        val stream = RedisHealthStream(serviceName, id).health

        RedisScope.start()
        RedisScope.launch {
            val producer = ctx.eventStreamService.createProducer(stream)
            val ourLock = lock!!
            while (isActive) {
                try {
                    ourLock.acquire()
                    producer.produce(RedisHealthMessage(uuid, System.currentTimeMillis()))
                    delay(REDIS_HEALTH_STREAM_PAUSE_BETWEEN_EVENTS)
                } catch (ex: Throwable) {
                    log.warn(ex.stackTraceToString())
                }
            }
        }

        ctx.eventStreamService.subscribe(stream, EventConsumer.Immediate { event ->
            val now = System.currentTimeMillis()
            if (event.id == uuid && now - event.timestamp < REDIS_HEALTH_STREAM_MESSAGE_MAX_AGE) {
                lastObservedRedisMessage = now
            }
        })

        lastObservedRedisMessage = System.currentTimeMillis()
        return true
    }

    companion object Feature : MicroFeatureFactory<HealthCheckFeature, Unit>, Loggable {
        override val log = logger()
        override val key: MicroAttributeKey<HealthCheckFeature> = MicroAttributeKey("health-check-feature")
        override fun create(config: Unit): HealthCheckFeature = HealthCheckFeature()

        private const val MAX_REDIS_LOCKS = 8192
        private const val REDIS_HEALTH_STREAM_PAUSE_BETWEEN_EVENTS = 1000L * 60
        private const val REDIS_HEALTH_STREAM_MESSAGE_MAX_AGE = 1000L * 240
        private const val REDIS_HEALTH_MAX_MISSED_MESSAGES = 5
    }
}
