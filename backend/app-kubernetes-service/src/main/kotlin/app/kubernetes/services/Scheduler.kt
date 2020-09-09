package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import java.util.*
import java.util.concurrent.atomic.*
import kotlin.collections.HashMap
import kotlin.math.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

enum class ScheduledJobState {
    AWAITING_SCHEDULE,
    SCHEDULED,
    RUNNING,
    TERMINATED
}

data class SchedulingConstraints(
    val weekdays: List<Int>,
    val hourOfDayStart: Int,
    val hourOfDayEnd: Int
)

data class Node(
    val id: String,
    val vcpu: Int,
    val ram: Int,
    val freeList: FreeList = FreeList(vcpu, ram)
)

private data class FreeSlot(
    var start: Long,
    var end: Long,
    var vCpuAvailable: Int,
    var ramAvailable: Int,
    var nextSlot: FreeSlot? = null,
    var previousSlot: FreeSlot? = null,
    val jobs: ArrayList<String> = ArrayList()
) {
    override fun toString() = "FreeSlot($start, $end, $vCpuAvailable, $ramAvailable, $jobs, ${nextSlot != null})"
}

object TimeCollector {
    data class TimeSlot(val count: AtomicLong = AtomicLong(0L), val time: AtomicLong = AtomicLong(0L)) {
        override fun toString(): String {
            return "TimeSlot(count=$count, time=$time, avg=${time.get() / count.get().toDouble()})"
        }
    }
    val timers = HashMap<String, TimeSlot>()
}

private inline fun <R> timedFunction(name: String, block: () -> R): R {
    if (!TimeCollector.timers.contains(name)) {
        TimeCollector.timers[name] = TimeCollector.TimeSlot()
    }

    val start = Time.now()
    try {
        return block()
    } finally {
        val duration = Time.now() - start
        val value: TimeCollector.TimeSlot = TimeCollector.timers[name]!!
        value.time.addAndGet(duration)
        value.count.addAndGet(1)
    }
}

@OptIn(ExperimentalTime::class)
class FreeList(private val totalCpu: Int, private val totalRam: Int) {
    private val firstSlot = FreeSlot(0, Long.MAX_VALUE, totalCpu, totalRam)

    fun findJobsAt(timestamp: Long): List<String> {
        var currentSlot: FreeSlot? = firstSlot
        while (currentSlot != null) {
            if (timestamp in currentSlot.start..currentSlot.end) {
                return ArrayList(currentSlot.jobs)
            }
            currentSlot = currentSlot.nextSlot
        }
        return emptyList()
    }

    fun hasSpaceAt(start: Long, end: Long, vCpuReservation: Int, ramReservation: Int): Boolean {
        timedFunction("hasSpaceAt") {
            var currentSlot: FreeSlot? = firstSlot
            var combinedTime = 0L
            var startFound = false
            val totalTime = end - start

            while (currentSlot != null) {
                if (start >= currentSlot.start && start <= currentSlot.end) {
                    startFound = true
                } else if (startFound) {
                    return false
                }

                val hasSpace = currentSlot.vCpuAvailable >= vCpuReservation &&
                    currentSlot.ramAvailable >= ramReservation
                when {
                    startFound && hasSpace -> {
                        combinedTime += currentSlot.end - max(currentSlot.start, start)
                    }

                    startFound && !hasSpace -> {
                        return false
                    }
                }

                if (combinedTime >= totalTime) {
                    return true
                }

                currentSlot = currentSlot.nextSlot
            }

            return false
        }
    }

    fun findFreeSlot(vCpuReservation: Int, ramReservation: Int, duration: Long): Long? = timedFunction("findFreeSlot") {
        var currentSlot: FreeSlot? = firstSlot

        while (currentSlot != null) {
            if (currentSlot.vCpuAvailable >= vCpuReservation && currentSlot.ramAvailable >= ramReservation) {
                var combinedTime = currentSlot.end - currentSlot.start
                if (combinedTime >= duration) {
                    return currentSlot.start
                }

                // Not enough time in a single slot, we need to start searching
                var lastSlot: FreeSlot? = currentSlot.nextSlot
                while (lastSlot != null && combinedTime < duration) {
                    if (lastSlot.vCpuAvailable >= vCpuReservation && lastSlot.ramAvailable >= ramReservation) {
                        combinedTime += lastSlot.end - lastSlot.start
                        lastSlot = lastSlot.nextSlot
                    } else {
                        break
                    }
                }

                if (combinedTime >= duration) {
                    return currentSlot.start
                }
            }

            currentSlot = currentSlot.nextSlot
        }

        return null
    }

    fun addReservation(
        start: Long,
        end: Long,
        vCpuReservation: Int,
        ramReservation: Int,
        jobId: String
    ) = timedFunction("addReservation") {
        // Find all slots we overlap with
        // If we are fully contained within (i.e. larger or same size) the slot then we can simply adjust the values
        // If we are smaller then split the slot

        var currentSlot: FreeSlot? = firstSlot
        while (currentSlot != null) {
            if (start >= currentSlot.start && start <= currentSlot.end) {
                // We are contained within the slot
                currentSlot.vCpuAvailable -= vCpuReservation
                currentSlot.ramAvailable -= ramReservation

                assert(currentSlot.vCpuAvailable >= 0)
                assert(currentSlot.ramAvailable >= 0)

                if (end < currentSlot.end) {
                    // We are smaller than this slot, we need to split it
                    val newSlot = currentSlot.copy(
                        start = end + 1,
                        vCpuAvailable = currentSlot.vCpuAvailable + vCpuReservation,
                        ramAvailable = currentSlot.ramAvailable + ramReservation,
                        previousSlot = currentSlot,
                        jobs = ArrayList(currentSlot.jobs)
                    )
                    currentSlot.nextSlot = newSlot
                    currentSlot.end = end
                    currentSlot.jobs.add(jobId)
                }
            }

            if (end <= currentSlot.start) {
                break // No need to search any further
            }

            currentSlot = currentSlot.nextSlot
        }
    }

    fun releaseReservation(
        start: Long,
        end: Long,
        vCpuReservation: Int,
        ramReservation: Int
    ): Unit = timedFunction("releaseReservation") {
        TODO("Do the reverse of addReservation but this time we should attempt to combine slots together")
    }
}


data class Schedule(
    val nodes: List<String>,
    val startOfJob: Long,
    val endOfJob: Long
)

data class JobToBeScheduled(
    val timeRequired: SimpleDuration,
    val nodesRequired: Int,
    val vCpuReservation: Int,
    val ramReservation: Int,
    val currentState: ScheduledJobState = ScheduledJobState.AWAITING_SCHEDULE,
    val constraints: SchedulingConstraints = SchedulingConstraints((1..7).toList(), 0, 24),
    val schedule: Schedule? = null,
    val id: String = "",
)

sealed class SchedulerEvent {
    data class JobTerminated(val id: String) : SchedulerEvent()
    data class NodeOffline(val id: String) : SchedulerEvent()
    data class NodeOnline(val node: Node) : SchedulerEvent()
}

interface SchedulerCallbacks {
    fun scheduleJob(jobId: String, nodeId: String)
    fun terminateJob(jobId: String, nodeId: String)
    fun notifyJobUpdate(job: JobToBeScheduled)
}

class Scheduler(
    private val callbacks: SchedulerCallbacks,
    private val scope: BackgroundScope,
    private val numberOfSamples: Int = 5
) {
    private val nodes = HashMap<String, Node>()
    private val nodeIds = ArrayList<String>()

    private val jobQueue = Channel<JobToBeScheduled>()
    private val eventQueue = Channel<SchedulerEvent>()

    suspend fun addToQueue(job: JobToBeScheduled): String {
        val newId = "${idPrefix}_${idCounter.getAndIncrement()}"
        jobQueue.send(job.copy(id = newId))
        return newId
    }

    suspend fun notifyEvent(event: SchedulerEvent) {
        eventQueue.send(event)
    }

    fun start(): Job {
        return scope.launch {
            while (isActive) {
                select<Unit> {
                    jobQueue.onReceive { job ->
                        schedule(job)
                    }

                    eventQueue.onReceive { ev ->
                        when (ev) {
                            is SchedulerEvent.JobTerminated -> {

                            }

                            is SchedulerEvent.NodeOffline -> {
                                val node = nodes[ev.id] ?: run {
                                    log.warn("Unknown node '${ev.id}' has gone offline")
                                    return@onReceive
                                }

                                val currentJobs = node.freeList.findJobsAt(Time.now())
                                for (jobId in currentJobs) {
                                    // TODO Notify that job is dead
                                }

                                nodes.remove(ev.id)
                                nodeIds.remove(ev.id)
                            }

                            is SchedulerEvent.NodeOnline -> {
                                nodes[ev.node.id] = ev.node
                                nodeIds.add(ev.node.id)
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun schedule(job: JobToBeScheduled) = timedFunction("schedule") {
        coroutineScope {
            while (true) {
                val solutions = (0 until numberOfSamples).map {
                    async {
                        val startingPoint = nodes[nodeIds[Random.nextInt(nodes.size)]]!!
                        val proposedTime = startingPoint.freeList.findFreeSlot(
                            job.vCpuReservation,
                            job.ramReservation,
                            job.timeRequired.toMillis()
                        ) ?: return@async null
                        val end = proposedTime + job.timeRequired.toMillis()

                        val nodesFound = ArrayList<String>()
                        nodesFound.add(startingPoint.id)

                        if (job.nodesRequired > 1) {
                            val startNode = Random.nextInt(nodes.size)
                            for (attempt in nodeIds.indices) {
                                if (nodesFound.size == job.nodesRequired) break
                                val node = nodes[nodeIds[(startNode + attempt) % nodeIds.size]]!!
                                if (node.id == startingPoint.id) continue

                                val hasSpace = node.freeList.hasSpaceAt(
                                    proposedTime,
                                    end,
                                    job.vCpuReservation,
                                    job.ramReservation
                                )

                                if (hasSpace) nodesFound.add(node.id)
                            }
                        }

                        if (nodesFound.size == job.nodesRequired) Schedule(nodesFound, proposedTime, end)
                        else null
                    }
                }.awaitAll()

                val solution = solutions.minByOrNull { it?.startOfJob ?: Long.MAX_VALUE } ?: continue
                solution.nodes.forEach { node ->
                    nodes[node]!!.freeList.addReservation(
                        solution.startOfJob,
                        solution.endOfJob,
                        job.vCpuReservation,
                        job.ramReservation,
                        job.id
                    )
                }

                callbacks.notifyJobUpdate(
                    job.copy(
                        schedule = solution,
                        currentState = ScheduledJobState.SCHEDULED
                    )
                )
                break
            }
        }
    }

    private suspend fun reoderQueue() {

    }

    companion object : Loggable {
        override val log = logger()
        private val idCounter = AtomicLong(0)
        private val idPrefix = UUID.randomUUID().toString()
    }
}

fun main() {
    val scope = BackgroundScope()
    scope.init()
    val scheduler = Scheduler(
        object : SchedulerCallbacks {
            val histogram = HashMap<Long, Int>()
            var scheduled = 0
            var last = Time.now()
            var total = 0L
            override fun scheduleJob(jobId: String, nodeId: String) {
                println("scheduleJob($jobId, $nodeId)")
            }

            override fun terminateJob(jobId: String, nodeId: String) {
                println("terminateJob($jobId, $nodeId)")
            }

            override fun notifyJobUpdate(job: JobToBeScheduled) {
                scheduled++
                /*
                println(job.id)
                println(job.schedule)
                histogram[job.schedule!!.startOfJob] = (histogram[job.schedule.startOfJob] ?: 0) + 1
                println(histogram.keys.sorted().joinToString(", ") { "$it = ${histogram[it]}"})
                 */

                if (scheduled % 1000 == 0) {
                    val now = Time.now()
                    val time = now - last
                    total += time
                    println("dt: $time, total: $total, scheduled: ${scheduled}")
                    println(TimeCollector.timers.entries.joinToString("\n  ") { "${it.key} = ${it.value}" })
                    last = now
                }
            }
        },
        scope,
        1
    )

    val job = scheduler.start()
    runBlocking {
        repeat(100) {
            scheduler.notifyEvent(
                SchedulerEvent.NodeOnline(Node("n-$it", 30, 30))
            )
        }

        // We can run 10 jobs in parallel, which means we should be able to fit this in 200 cycles (201_000 largest endOfJob)
        repeat(1_000_000) {
            scheduler.addToQueue(
                JobToBeScheduled(
                    SimpleDuration(0, 0, 1),
                    10,
                    30,
                    30,
                )
            )
        }

        println("Terminating in 5 seconds")
        delay(5000)
        println("Goodbye")
    }
    job.cancel()
    scope.stop()
}
