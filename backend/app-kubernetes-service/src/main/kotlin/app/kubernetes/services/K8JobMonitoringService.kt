package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.watcher.api.JobEvents
import dk.sdu.cloud.app.orchestrator.api.ComputationCallbackDescriptions
import dk.sdu.cloud.app.orchestrator.api.JobCompletedRequest
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.SubmitComputationResult
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.types.BinaryStream
import dk.sdu.cloud.events.EventConsumer
import dk.sdu.cloud.events.EventStreamService
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.DistributedState
import dk.sdu.cloud.service.DistributedStateFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.create
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.withLock
import io.ktor.http.HttpStatusCode
import io.ktor.util.cio.readChannel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

const val LOCK_PREFIX = "lock-app-k8-job-"
const val STATUS_PREFIX = "status-app-k8-job-"

/**
 * Monitors life-time events of Kubernetes jobs.
 */
class K8JobMonitoringService(
    private val k8: K8Dependencies,
    private val lockFactory: DistributedLockFactory,
    private val eventStreamService: EventStreamService,
    private val stateFactory: DistributedStateFactory,
    private val logService: K8LogService
) {
    private fun getLock(jobId: String): DistributedLock = lockFactory.create(LOCK_PREFIX + jobId)

    private fun getCompletionState(jobId: String): DistributedState<Boolean> =
        stateFactory.create(STATUS_PREFIX + jobId, 1000L * 60 * 60 * 24)

    fun initializeListeners() {
        eventStreamService.subscribe(JobEvents.events, EventConsumer.Immediate { (jobName, condition) ->
            log.debug("Received Kubernetes Event: $jobName $condition")
            val jobId = k8.nameAllocator.reverseLookupJobName(jobName) ?: return@Immediate

            // Check for failure
            if (condition != null && condition.type == "Failed" && condition.reason == "DeadlineExceeded") {
                k8.scope.launch {
                    getLock(jobId).withLock {
                        val state = getCompletionState(jobId)
                        if (state.get() != true) {
                            k8.changeState(jobId, JobState.TRANSFER_SUCCESS, "Job did not complete within deadline.")
                            val userPod = k8.nameAllocator.listPods(jobId).firstOrNull()?.metadata?.name
                            transferLogAndMarkAsCompleted(jobId, userPod, null, false)
                            state.set(true)
                        }
                    }
                }

                return@Immediate
            }

            val allPods = k8.nameAllocator.listPods(jobId)
            log.debug("Found ${allPods.size} pods")
            if (allPods.isEmpty()) return@Immediate

            var isDone = true
            var maxDurationInMillis = 0L
            var isSuccess = true
            for (pod in allPods) {
                log.debug(
                    "Pod container status: " +
                            pod.status.containerStatuses.joinToString(", ") { "${it.name} ${it.state.terminated}" }
                )

                val userContainer = pod.status.containerStatuses.find { it.name == USER_CONTAINER } ?: return@Immediate
                val containerState = userContainer.state.terminated

                if (containerState == null || containerState.startedAt == null) {
                    isDone = false
                    break
                }

                val startAt = ZonedDateTime.parse(containerState.startedAt).toInstant().toEpochMilli()
                val finishedAt =
                    ZonedDateTime.parse(containerState.finishedAt).toInstant().toEpochMilli()

                // We add 5 seconds for just running the application.
                // It seems unfair that a job completing instantly is accounted for nothing.
                val duration = ((finishedAt - startAt) + 5_000)
                if (duration > maxDurationInMillis) {
                    maxDurationInMillis = duration
                }

                if (containerState.exitCode != 0) {
                    isSuccess = false
                }
            }

            if (isDone) {
                val resource = allPods.first()

                k8.scope.launch {
                    getLock(jobId).withLock {
                        val state = getCompletionState(jobId)

                        if (state.get() != true) {
                            val duration = SimpleDuration.fromMillis(maxDurationInMillis)
                            log.info("App finished in $duration")

                            k8.changeState(
                                jobId,
                                JobState.TRANSFER_SUCCESS,
                                "Job has finished. Total duration: $duration."
                            )
                            transferLogAndMarkAsCompleted(jobId, resource.metadata.name, duration, isSuccess)
                            state.set(true)
                        }
                    }
                }
            }
        })
    }

    suspend fun runPostCreateHandlers(
        verifiedJob: VerifiedJob,
        jobConfiguration: suspend () -> Unit
    ) {
        log.info("Awaiting container start!")

        @Suppress("TooGenericExceptionCaught")
        try {
            jobConfiguration()
            log.debug("Job has been configured")

            // We cannot really provide a better message. We truly do not know what is going on with the job.
            // It might be pulling stuff, it might be in the queue. Really, we have no idea what is happening
            // with it. It does not appear that Kubernetes is exposing any of this information to us.
            //
            // We don't really care about a failure in this one
            k8.addStatus(verifiedJob.id, "Your job is currently waiting to be scheduled. This step might take a while.")

            awaitCatching(retries = 36_000 * 24, time = 100) {
                val pods = k8.nameAllocator.listPods(verifiedJob.id)
                check(pods.isNotEmpty()) { "Found no pods for job!" }

                pods.all { pod ->
                    // Note: We are awaiting the user container
                    val state = pod.status.containerStatuses.first().state
                    state.running != null || state.terminated != null
                }
            }

            getLock(verifiedJob.id).withLock {
                // We need to hold the lock until we get a response to avoid race conditions.
                k8.changeState(
                    verifiedJob.id,
                    JobState.RUNNING,
                    "Your job is now running. You will be able to follow the logs while the job is running."
                )
            }
        } catch (ex: Throwable) {
            getLock(verifiedJob.id).withLock {
                val state = getCompletionState(verifiedJob.id)
                if (state.get() != true) {
                    log.warn("Container did not start within deadline!")
                    log.debug(ex.stackTraceToString())
                    k8.changeState(verifiedJob.id, JobState.FAILURE, "Job did not start within deadline.")
                    state.set(true)
                }
            }
        }
    }

    fun cancel(verifiedJob: VerifiedJob) {
        val pod = k8.nameAllocator.listPods(verifiedJob.id).firstOrNull()
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        k8.scope.launch {
            getLock(verifiedJob.id).withLock {
                val state = getCompletionState(verifiedJob.id)
                if (state.get() != true) {
                    transferLogAndMarkAsCompleted(verifiedJob.id, pod.metadata.name, null, true)
                    k8.nameAllocator.findJobs(verifiedJob.id).delete()
                    state.set(true)
                }
            }
        }
    }

    private suspend fun transferLogAndMarkAsCompleted(
        jobId: String,
        podName: String?,
        duration: SimpleDuration?,
        success: Boolean
    ) {
        if (podName != null) {
            val logFile = logService.downloadLog(podName)
            if (logFile != null) {
                try {
                    ComputationCallbackDescriptions.submitFile.call(
                        SubmitComputationResult(
                            jobId,
                            "stdout.txt",
                            BinaryStream.outgoingFromChannel(logFile.readChannel(), logFile.length())
                        ),
                        k8.serviceClient
                    ).orThrow()
                } catch (ex: Throwable) {
                    log.warn("Caught exception while attempting to submit log file! ${ex.stackTraceToString()}")
                }
            }
        }

        ComputationCallbackDescriptions.completed.call(
            JobCompletedRequest(jobId, duration, success),
            k8.serviceClient
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
