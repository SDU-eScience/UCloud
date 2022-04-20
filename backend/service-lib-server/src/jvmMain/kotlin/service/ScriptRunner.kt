package dk.sdu.cloud.service

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.events.JsonEventStream
import dk.sdu.cloud.events.RedisConnectionManager
import dk.sdu.cloud.micro.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

data class Script(
    val metadata: ScriptMetadata,
    val script: suspend () -> Unit
)

@Serializable
data class StartScriptRequest(val scriptId: String)

@Serializable
data class ScriptErrorReport(val metadata: ScriptMetadata, val errorMessage: String)

class ScriptManager : MicroFeature {
    private val localScripts = ArrayList<Script>()
    var onRegistration: (suspend (metadata: ScriptMetadata) -> Unit)? = null
    var onError: (suspend (metadata: ScriptErrorReport) -> Unit)? = null

    private var redisConnectionManager: RedisConnectionManager? = null
    private var redisBroadcastingStream: RedisBroadcastingStream? = null
    private var myLock: DistributedLock? = null

    private val lastRunFallback = Collections.synchronizedMap(HashMap<String, Long>()) as MutableMap<String, Long>

    private val registrationRequestStream = JsonEventStream("script-registration-requests", Unit.serializer())
    private val registrationStream = JsonEventStream("script-registrations", ScriptMetadata.serializer())
    private val startStream = JsonEventStream("script-start-requests", StartScriptRequest.serializer())
    private val errorStream = JsonEventStream("script-error-stream", ScriptErrorReport.serializer())

    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        if (!cliArgs.contains("--no-scripts")) {
            redisConnectionManager = runCatching { ctx.redisConnectionManager }.getOrNull()
            redisBroadcastingStream = redisConnectionManager?.let { RedisBroadcastingStream(it) }
            myLock = redisConnectionManager?.let {
                DistributedLockBestEffort(ctx.serviceDescription.name, connectionManager = it)
            }

            startScriptRunner(ctx)
        }
    }

    fun register(script: Script) {
        runBlocking {
            localScripts.add(script)
            redisBroadcastingStream?.broadcast(script.metadata, registrationStream)
        }
    }

    suspend fun lastRun(metadata: ScriptMetadata): Long {
        val redisConnectionManager = redisConnectionManager
        return if (redisConnectionManager != null) {
            val state = RedisDistributedState(
                "script-last-run-${metadata.id}",
                null,
                redisConnectionManager,
                Long.serializer()
            )

            state.get() ?: 0L
        } else {
            lastRunFallback[metadata.id] ?: 0L
        }
    }

    private suspend fun updateLastRun(metadata: ScriptMetadata) {
        val connManager = redisConnectionManager
        if (connManager != null) {
            val state = RedisDistributedState(
                "script-last-run-${metadata.id}",
                null,
                connManager,
                Long.serializer()
            )

            state.set(Time.now())
        } else {
            lastRunFallback[metadata.id] = Time.now()
        }
    }

    private suspend fun reportError(metadata: ScriptMetadata, error: String) {
        redisBroadcastingStream?.broadcast(ScriptErrorReport(metadata, error), errorStream)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runScript(script: Script) {
        val lock = myLock
        if (lock == null) {
            try {
                updateLastRun(script.metadata)
                log.info("Starting script: ${script.metadata}")
                val time = measureTime { script.script() }
                log.info("Script has finished executing, it took $time")
            } catch (ex: Throwable) {
                log.warn("Caught an exception while running script: ${script.metadata}\n${ex.stackTraceToString()}")
                reportError(script.metadata, ex.stackTraceToString())
            }
        } else {
            if (lock.acquire()) {
                try {
                    updateLastRun(script.metadata)
                    log.info("Starting script: ${script.metadata}")
                    val time = measureTime { script.script() }
                    log.info("Script has finished executing, it took $time")
                } catch (ex: Throwable) {
                    log.warn("Caught an exception while running script: ${script.metadata}\n${ex.stackTraceToString()}")
                    reportError(script.metadata, ex.stackTraceToString())
                } finally {
                    lock.release()
                }
            }
        }
    }

    private fun startScriptRunner(ctx: Micro) {
        val executor = Executors.newSingleThreadExecutor()
        val dispatcher = executor.asCoroutineDispatcher()
        val supervisor = SupervisorJob()
        val context = dispatcher + supervisor
        val scope = CoroutineScope(context)

        val stream = redisBroadcastingStream
        if (stream != null) {
            scope.launch {
                while (isActive) {
                    if (!ucloudIsReady.get()) {
                        delay(500)
                        continue
                    } else {
                        break
                    }
                }

                try {
                    stream.subscribe(startStream) { event ->
                        try {
                            val myScript = localScripts.find { it.metadata.id == event.scriptId }
                                ?: return@subscribe

                            runScript(myScript)
                        } catch (ex: Throwable) {
                            log.warn(ex.stackTraceToString())
                        }
                    }

                    stream.subscribe(registrationRequestStream) { _ ->
                        for (script in localScripts) {
                            stream.broadcast(script.metadata, registrationStream)
                        }
                    }

                    val onRegistration = this@ScriptManager.onRegistration
                    if (onRegistration != null)  {
                        stream.subscribe(registrationStream) { metadata ->
                            try {
                                onRegistration(metadata)
                            } catch (ex: Throwable) {
                                log.warn("Caught exception while handling registration: ${ex.stackTraceToString()}")
                            }
                        }
                    }

                    val onError = this@ScriptManager.onError
                    if (onError != null) {
                        stream.subscribe(errorStream) { report ->
                            try {
                                onError(report)
                            } catch (ex: Throwable) {
                                log.warn("Caught exception while handling error report: ${ex.stackTraceToString()}")
                            }
                        }
                    }
                } catch (ex: Throwable) {
                    log.warn(ex.stackTraceToString())
                    delay(5000)
                }
            }
        }

        scope.launch {
            while (isActive) {
                if (!ucloudIsReady.get()) {
                    delay(500)
                    continue
                }

                val now = Time.now()
                val nowAsDate = LocalDateTime.ofInstant(Date(now).toInstant(), ZoneId.systemDefault())

                for (script in localScripts) {
                    val scriptLastRun = lastRun(script.metadata)
                    val scriptLastRunAsDate = LocalDateTime.ofInstant(
                        Date(scriptLastRun).toInstant(),
                        ZoneId.systemDefault()
                    )

                    val shouldRun: Boolean = when (val instructions = script.metadata.whenToStart) {
                        is WhenToStart.Periodically -> {
                            now - scriptLastRun >= instructions.timeBetweenInvocationInMillis
                        }

                        is WhenToStart.Daily -> {
                            val nextRunAt = scriptLastRunAsDate.plusDays(1L)
                                .withHour(instructions.hour).withMinute(instructions.minute).withSecond(0)
                                .withNano(0)
                            nowAsDate >= nextRunAt
                        }

                        is WhenToStart.Weekly -> {
                            val nextRunAt = scriptLastRunAsDate.plusWeeks(1L)
                                .withHour(instructions.hour).withMinute(instructions.minute).withSecond(0)
                                .withNano(0)
                            nowAsDate >= nextRunAt
                        }
                        is WhenToStart.Never -> {
                            false
                        }
                    }

                    if (shouldRun) {
                        runScript(script)
                    }
                }

                delay(10_000)
            }
        }

        ctx.feature(DeinitFeature).addHandler {
            runCatching {
                dispatcher.cancel()
                executor.shutdown()
            }
        }
    }

    suspend fun requestRegistration() {
        val stream = redisBroadcastingStream ?: error("No broadcast stream available")
        stream.broadcast(Unit, registrationRequestStream)
    }

    suspend fun requestRun(scriptId: String) {
        val stream = redisBroadcastingStream ?: error("No broadcast stream available")
        stream.broadcast(StartScriptRequest(scriptId), startStream)
    }

    companion object : Loggable, MicroFeatureFactory<ScriptManager, Unit> {
        override val log = logger()

        override val key: MicroAttributeKey<ScriptManager> = MicroAttributeKey("script-runner")
        override fun create(config: Unit): ScriptManager = ScriptManager()
    }
}
