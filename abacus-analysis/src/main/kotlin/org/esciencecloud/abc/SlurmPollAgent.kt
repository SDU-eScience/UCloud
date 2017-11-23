package org.esciencecloud.abc

import kotlinx.coroutines.experimental.runBlocking
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.processors.SlurmProcessor
import org.esciencecloud.abc.ssh.SSHConnectionPool
import org.esciencecloud.abc.ssh.pollSlurmStatus
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
        private val pollUnit: TimeUnit,
        private val processor: SlurmProcessor
) {
    private var lastPoll = ZonedDateTime.now()
    private var future: ScheduledFuture<*>? = null
    private val active = HashSet<Long>()
    private val lock = Any()

    private val log = LoggerFactory.getLogger(SlurmPollAgent::class.java)

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
        }
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

                    processor.handle(it)
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