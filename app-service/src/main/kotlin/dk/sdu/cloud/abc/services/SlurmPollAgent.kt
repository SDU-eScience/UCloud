package dk.sdu.cloud.abc.services

import dk.sdu.cloud.abc.api.HPCAppEvent
import dk.sdu.cloud.abc.services.ssh.SSHConnectionPool
import dk.sdu.cloud.abc.services.ssh.pollSlurmStatus
import kotlinx.coroutines.experimental.runBlocking
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
        future = executor.scheduleAtFixedRate({ tick() }, initialDelay, pollInterval, pollUnit)
    }

    fun handle(event: HPCAppEvent) {
        when (event) {
            is HPCAppEvent.SuccessfullyCompleted -> {
                log.debug("Removing ${event.jobId}")
                synchronized(lock) { active.remove(event.jobId) }
            }
            is HPCAppEvent.Pending -> {
                log.debug("Activating ${event.jobId}")
                synchronized(lock) { active.add(event.jobId) }
            }

            else -> {
                // Do nothing
            }
        }
    }

    fun addListener(listener: SlurmEventListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: SlurmEventListener) {
        listeners.remove(listener)
    }

    private fun tick() {
        log.debug("Ticking: ${active.size}")
        if (active.isEmpty()) return

        ssh.use {
            val events = pollSlurmStatus(lastPoll)
            runBlocking {
                synchronized(lock) {
                    events.filter { it.jobId in active }
                }.forEach {
                    if (it is SlurmEventEnded) {
                        log.debug("Removing ${it.jobId}")
                        active.remove(it.jobId)
                    }

                    listeners.forEach { listener -> listener(it) }
                }
            }
            lastPoll = ZonedDateTime.now()
        }
    }

    fun stop() {
        log.info("Stopping slurm poll agent")

        future!!.cancel(true)
    }
}