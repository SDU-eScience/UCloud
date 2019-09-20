package dk.sdu.cloud.micro

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * A feature for initializing a [BackgroundScope]
 *
 * @see BackgroundScope
 */
class BackgroundScopeFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        val scope = BackgroundScope()
        scope.init()

        ctx.backgroundScope = scope
        ctx.ioDispatcher = scope.dispatcher

        ctx.feature(DeinitFeature).addHandler {
            ctx.backgroundScope.stop()
        }
    }

    companion object : MicroFeatureFactory<BackgroundScopeFeature, Unit> {
        override val key = MicroAttributeKey<BackgroundScopeFeature>("background-scope-feature")
        override fun create(config: Unit): BackgroundScopeFeature = BackgroundScopeFeature()
    }
}

/**
 * The [BackgroundScope] is a [CoroutineScope] which can safely be used for scheduling blocking I/O tasks.
 *
 * Use [Micro.backgroundScope] if the job should truly be scheduled "in the background". Use [Micro.ioDispatcher] if
 * you simply need a dispatcher capable of handling many blocking I/O tasks. [Micro.backgroundScope] is using
 * [Micro.ioDispatcher] as its dispatcher. Closing [Micro.backgroundScope] will close [Micro.ioDispatcher].
 *
 * [Micro.ioDispatcher] is backed by [Executors.newCachedThreadPool].
 *
 * When programming services you should strive towards using non-blocking I/O. It should perform significantly better
 * and is the only real way to do coroutines. In some conditions we do, however, need to use a library which performs
 * blocking I/O. It is not always feasible to find an alternative to these libraries. For these situations their jobs
 * should be scheduled in a [CoroutineScope]/[CoroutineDispatcher] which is capable of handling blocking I/O.
 *
 * The [Dispatchers.IO] dispatcher is unsuitable for this if we are dealing with user requests. The
 * [Dispatchers.IO] dispatcher will never create more than `max(64, coreCount)` threads. If this is a long running
 * blocking process for a user we will run out of threads quickly.
 */
class BackgroundScope : CoroutineScope {
    internal lateinit var dispatcher: CoroutineDispatcher
    private var executor: ExecutorService? = null

    override val coroutineContext: CoroutineContext
        get() = dispatcher

    fun init() {
        log.debug("Calling init()")
        synchronized(this) {
            if (executor == null) {
                log.info("Initializing BackgroundScope")
                val newCachedThreadPool = Executors.newCachedThreadPool()
                executor = newCachedThreadPool
                dispatcher = newCachedThreadPool.asCoroutineDispatcher()
            }
        }
    }

    fun stop() {
        log.debug("Calling stop()")
        executor?.shutdown()
        executor = null
    }

    fun reset() {
        log.debug("Calling reset()")
        val executor = executor
        if (executor != null) {
            log.info("Resetting BackgroundScope")
            stop()
            while (!executor.isShutdown) {
                Thread.sleep(10)
            }
        }

        init()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

private val scopeKey = MicroAttributeKey<BackgroundScope>("background-scope")

/**
 * @see BackgroundScope
 */
var Micro.backgroundScope: BackgroundScope
    get() {
        requireFeature(BackgroundScopeFeature)
        return attributes[scopeKey]
    }
    internal set(value) {
        attributes[scopeKey] = value
    }


private val dispatcherKey = MicroAttributeKey<CoroutineDispatcher>("background-dispatcher")

/**
 * @see BackgroundScope
 */
var Micro.ioDispatcher: CoroutineDispatcher
    get() {
        requireFeature(BackgroundScopeFeature)
        return attributes[dispatcherKey]
    }
    internal set(value) {
        attributes[dispatcherKey] = value
    }
