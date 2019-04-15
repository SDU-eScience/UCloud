package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.app.abacus.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.abacus.services.ssh.pollSlurmStatus
import dk.sdu.cloud.app.abacus.services.ssh.use
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class SlurmPollAgent(
    private val ssh: SSHConnectionPool,
    private val executor: ScheduledExecutorService,
    private val initialDelay: Long,
    private val pollInterval: Long,
    private val pollUnit: TimeUnit
) {
    private var lastPoll = ZonedDateTime.now()
    private var future: ScheduledFuture<*>? = null
    private val active = HashSet<Long>()
    private val lock = Any()

    private val log = LoggerFactory.getLogger(SlurmPollAgent::class.java)

    private val listeners = ArrayList<SlurmEventListener>()

    fun start() {
        log.info("Starting slurm poll agent")

        if (future != null) throw IllegalStateException("Already started!")
        future = executor.scheduleAtFixedRate({ runBlocking { tick() } }, initialDelay, pollInterval, pollUnit)
    }

    fun startTracking(slurmId: Long) {
        log.debug("Activating $slurmId")
        synchronized(lock) { active.add(slurmId) }
    }

    fun addListener(listener: SlurmEventListener): SlurmEventListener {
        listeners.add(listener)
        return listener
    }

    fun removeListener(listener: SlurmEventListener) {
        listeners.remove(listener)
    }

    @Suppress("TooGenericExceptionCaught") // For now
    private suspend fun tick() {
        try {
            if (active.isNotEmpty()) log.debug("Ticking: ${active.size}")
            if (active.isEmpty()) return

            ssh.use {
                val events = pollSlurmStatus()
                runBlocking {
                    synchronized(lock) {
                        events.filter { it.jobId in active }
                    }.forEach {
                        if (it is SlurmEventEnded || it is SlurmEventFailed || it is SlurmEventTimeout) {
                            log.debug("Removing ${it.jobId}")
                            active.remove(it.jobId)
                        }

                        listeners.forEach { listener -> listener(it) }
                    }

                    val toSkip = active.filter { it == SLURM_ID_SKIP }
                    toSkip.forEach {
                        val event = SlurmEventEnded(it)
                        listeners.forEach { listener -> listener(event) }
                    }
                    active.removeAll(toSkip)
                }
                lastPoll = ZonedDateTime.now()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun stop() {
        log.info("Stopping slurm poll agent")

        future!!.cancel(true)
    }

    companion object {
        const val SLURM_ID_SKIP = -1337L
    }
}
