package dk.sdu.cloud.events

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.lettuce.core.Consumer
import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.StreamMessage
import io.lettuce.core.XAddArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private object RedisScope : CoroutineScope {
    private lateinit var dispatcher: CoroutineDispatcher
    private var executor: ExecutorService? = null

    private val isRunning: Boolean
        get() = executor != null

    override val coroutineContext: CoroutineContext
        get() = dispatcher

    fun start() {
        synchronized(this) {
            if (isRunning) return

            val newCachedThreadPool = Executors.newCachedThreadPool()
            executor = newCachedThreadPool
            dispatcher = newCachedThreadPool.asCoroutineDispatcher()
        }
    }

    fun stop() {
        executor?.shutdown()
        executor = null
    }
}

class RedisStreamService(
    public val connManager: RedisConnectionManager,
    private val group: String,
    private val consumerId: String,
    private val parallelism: Int
) : EventStreamService {
    private suspend fun initializeStream(redis: RedisAsyncCommands<String, String>, stream: EventStream<*>) {
        if (redis.xlenA(stream.name) == 0L) {
            // We need to send an empty message (which is ignored during parsing) to initialize the stream.
            // The stream must exist before we create the group with xgroup.

            // We don't need to do any locking for this. It is okay to send multiple init messages.
            redis.xaddA(stream.name, STREAM_INIT_MSG)
        }
    }

    override fun <V : Any> subscribe(
        stream: EventStream<V>,
        consumer: EventConsumer<V>,
        rescheduleIdleJobsAfterMs: Long
    ) {
        RedisScope.start() // Start if we haven't already

        repeat(parallelism) {
            RedisScope.launch {
                run {
                    val redis = connManager.getConnection()
                    initializeStream(redis, stream)

                    // Create the group or fail if it already exists. We just ignore the exception assuming it means
                    // that it already exists. If it failed entirely we will fail once we start consumption.
                    runCatching { redis.xgroupA(stream.name, group) }
                }

                val messagesNotYetAcknowledged = HashSet<String>()
                suspend fun RedisAsyncCommands<String, String>.dispatchToInternalConsumer(
                    messages: List<StreamMessage<String, String>>
                ) {
                    val events = messages.mapNotNull { message ->
                        val textValue = message.body?.values?.first() ?: return@mapNotNull null
                        if (textValue == STREAM_INIT_MSG) return@mapNotNull null

                        runCatching { stream.deserialize(textValue) }.getOrNull()
                    }

                    if (consumer is EventConsumer.Immediate) {
                        val ids = messages.map { it.id }
                        launch {
                            @Suppress("TooGenericExceptionCaught")
                            try {
                                if (consumer.accept(events)) {
                                    xackA(stream.name, group, ids.toSet())
                                }
                            } catch (ex: Throwable) {
                                log.warn("Caught exception while consuming from $stream")
                                log.warn(ex.stackTraceToString())
                            }
                        }
                    } else {
                        messagesNotYetAcknowledged.addAll(messages.map { it.id })
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            if (consumer.accept(events)) {
                                xackA(stream.name, group, messagesNotYetAcknowledged)
                                messagesNotYetAcknowledged.clear()
                            }
                        } catch (ex: Throwable) {
                            log.warn("Caught exception while consuming from $stream")
                            log.warn(ex.stackTraceToString())
                        }
                    }
                }

                var nextClaim = 0L
                fun setNextClaimTimer() = run { nextClaim = System.currentTimeMillis() + 30_000 }
                setNextClaimTimer()

                while (true) {
                    val redis = connManager.getConnection()
                    val sync = connManager.getSync()
                    if (System.currentTimeMillis() >= nextClaim) {
                        // We reschedule messages that weren't acknowledged if they have been idle fore more than
                        // minimumIdleTime. It goes through the normal consumption mechanism.
                        val pending = redis.xpendingA(stream.name, group)
                        val ids = pending.filter { it.msSinceLastAttempt >= rescheduleIdleJobsAfterMs }.map { it.id }

                        if (ids.isNotEmpty()) {
                            redis.dispatchToInternalConsumer(
                                redis.xclaimA(
                                    stream.name,
                                    group,
                                    consumerId,
                                    rescheduleIdleJobsAfterMs,
                                    ids
                                )
                            )
                        }

                        setNextClaimTimer()
                    } else {
                        redis.dispatchToInternalConsumer(
                            redis.xreadgroupA(
                                group, consumerId,
                                XReadArgs.StreamOffset.lastConsumed(stream.name),
                                offset = XReadArgs.Builder.block(50).count(50)
                            )
                        )
                    }
                }
            }
        }
    }


    override fun <V : Any> createProducer(stream: EventStream<V>): EventProducer<V> {
        RedisScope.start() // Start if we haven't already

        runBlocking {
            initializeStream(connManager.getConnection(), stream)
        }

        return RedisEventProducer(connManager, stream)
    }

    override fun createStreams(streams: List<EventStream<*>>) {
        // Streams are created when we open a subscription.
    }

    override fun describeStreams(names: List<String>): Map<String, EventStreamState?> {
        return names.map { it to null }.toMap()
    }

    override fun start() {
        // No need to start RedisScope here (started with first subscription)
    }

    override fun stop() {
        RedisScope.stop()
    }

    companion object : Loggable {
        override val log = logger()
        const val STREAM_INIT_MSG = "stream-init"
    }
}

class RedisConnectionManager(private val client: RedisClient) {
    private val mutex = Mutex()
    private var openConnection: RedisAsyncCommands<String, String>? = null
    private var syncConnection: RedisCommands<String, String>? = null

    suspend fun getConnection(): RedisAsyncCommands<String, String> {
        run {
            val conn = openConnection
            if (conn != null && conn.isOpen) return conn
        }

        mutex.withLock {
            val conn = openConnection
            if (conn != null && conn.isOpen) return conn

            val newConnection = client.connect().async()
            openConnection = newConnection
            return newConnection
        }
    }

    suspend fun getSync(): RedisCommands<String, String> {
        run {
            val conn = syncConnection
            if (conn != null && conn.isOpen) return conn
        }

        mutex.withLock {
            val conn = syncConnection
            if (conn != null && conn.isOpen) return conn

            val newConnection = client.connect().sync()
            syncConnection = newConnection
            return newConnection
        }
    }
}

class RedisEventProducer<V : Any>(
    private val connManager: RedisConnectionManager,
    override val stream: EventStream<V>
) : EventProducer<V> {

    override suspend fun produce(events: List<V>) {
        // In some cases the async API for this have been taking upwards of 40 seconds to return. We have no
        // such problems when using the sync API. We launch the job in the RedisScope which will use a cached
        // thread pool (suitable for blocking tasks).
        RedisScope.launch {
            val conn = connManager.getSync()
            events.forEach { event ->
                conn.xadd(
                    stream.name,
                    XAddArgs.Builder.maxlen(1_000_000L).approximateTrimming(true),
                    mapOf("msg" to stream.serialize(event))
                )
            }
        }.join()
    }
}

// Redis commands
private suspend fun RedisAsyncCommands<String, String>.xaddA(
    stream: String,
    message: String
) = suspendCoroutine<String> { cont ->
    xadd(stream, mapOf("msg" to message))
        .thenAccept { cont.resume(it) }
        .exceptionally { cont.resumeWithException(it); null }
}

private suspend fun RedisAsyncCommands<String, String>.xgroupA(
    stream: String,
    group: String
) = suspendCoroutine<Unit> { cont ->
    xgroupCreate(XReadArgs.StreamOffset.latest(stream), group)
        .thenAccept { cont.resume(Unit) }
        .exceptionally { cont.resumeWithException(it); null }
}

private suspend fun RedisAsyncCommands<String, String>.xlenA(
    stream: String
) = suspendCoroutine<Long> { cont ->
    xlen(stream).thenAccept { cont.resume(it) }.exceptionally { cont.resumeWithException(it); null }
}

private suspend fun RedisAsyncCommands<String, String>.xackA(
    stream: String,
    group: String,
    messageIds: Set<String>
) = suspendCoroutine<Long> { cont ->
    if (messageIds.isEmpty()) {
        cont.resume(0)
        return@suspendCoroutine
    }

    xack(stream, group, *messageIds.toTypedArray())
        .thenAccept { cont.resume(it) }
        .exceptionally {
            cont.resumeWithException(it)
            null
        }
}

private data class PendingMessage(
    val id: String,
    val consumerId: String,
    val msSinceLastAttempt: Long,
    val failureCount: Long
)

private suspend fun RedisAsyncCommands<String, String>.xpendingA(
    stream: String,
    group: String,
    limit: Long = 50L
) = suspendCoroutine<List<PendingMessage>> { cont ->
    xpending(stream, group, Range.create("-", "+"), Limit.from(limit))
        .thenAccept {
            cont.resume(
                it.map {
                    val list = it as List<*>
                    PendingMessage(
                        list[0] as String,
                        list[1] as String,
                        list[2] as Long,
                        list[3] as Long
                    )
                }
            )
        }
        .exceptionally {
            cont.resumeWithException(it)
            null
        }
}

private suspend fun RedisAsyncCommands<String, String>.xclaimA(
    stream: String,
    group: String,
    consumerId: String,
    minimumIdleTime: Long,
    ids: List<String>
) = suspendCoroutine<List<StreamMessage<String, String>>> { cont ->
    xclaim(stream, Consumer.from(group, consumerId), minimumIdleTime, *ids.toTypedArray())
        .thenAccept { cont.resume(it) }
        .exceptionally {
            cont.resumeWithException(it)
            null
        }
}

private suspend fun RedisAsyncCommands<String, String>.xreadgroupA(
    group: String,
    consumerId: String,
    vararg streams: XReadArgs.StreamOffset<String>,
    offset: XReadArgs? = XReadArgs.Builder.block(2000)
) = suspendCoroutine<List<StreamMessage<String, String>>> { cont ->
    xreadgroup(Consumer.from(group, consumerId), offset, *streams)
        .thenAccept { cont.resume(it) }
        .exceptionally {
            cont.resumeWithException(it)
            null
        }
}

