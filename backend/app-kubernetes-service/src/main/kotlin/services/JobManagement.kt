package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.DistributedLock
import dk.sdu.cloud.service.DistributedLockFactory
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.k8.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.minutes
import kotlin.time.seconds

interface JobManagementPlugin {
    suspend fun K8Dependencies.onCreate(job: VerifiedJob, builder: VolcanoJob) {}
    suspend fun K8Dependencies.onCleanup(jobId: String) {}
}

class JobManagement(
    private val k8: K8Dependencies,
    private val distributedLocks: DistributedLockFactory
) {
    private val plugins = ArrayList<JobManagementPlugin>()

    fun register(plugin: JobManagementPlugin) {
        plugins.add(plugin)
    }

    suspend fun create(verifiedJob: VerifiedJob) {
        val builder = VolcanoJob()
        val namespace = namespaceForJob(verifiedJob.id)
        builder.metadata = ObjectMeta(
            name = volcanoJobName(verifiedJob.id),
            namespace = namespace
        )
        builder.spec = VolcanoJob.Spec()
        plugins.forEach { with(it) { with(k8) { onCreate(verifiedJob, builder) } } }

        @Suppress("BlockingMethodInNonBlockingContext")
        k8.client.createResource(
            KubernetesResources.volcanoJob.withNamespace(namespace),
            defaultMapper.writeValueAsString(builder)
        )
    }

    private suspend fun namespaceForJob(jobId: String): String {
        return "app-kubernetes"
    }

    private fun volcanoJobName(jobId: String): String {
        // NOTE(Dan): This needs to be a valid DNS name. The job IDs currently use UUIDs as their ID, these are not
        // guaranteed to be valid DNS entries, but only because they might start with a number. As a result, we
        // prepend our jobs with a letter, making them valid DNS names.
        return "j-${jobId}"
    }

    suspend fun cleanup(jobId: String) {
        TODO()
    }

    private suspend fun onPodUpdate(pod: Pod) {
        TODO()
    }

    suspend fun initializeListeners() {
        k8.scope.launch {
            val lock = distributedLocks.create("app-k8-watcher", duration = 60_000)
            while (isActive) {
                try {
                    becomeMasterAndListen(lock)
                } catch (ex: Throwable) {
                    log.warn("An exception occurred while processing Kubernetes events")
                    log.warn(ex.stackTraceToString())
                }

                delay(15000 + Random.nextLong(5000))
            }
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    private suspend fun becomeMasterAndListen(lock: DistributedLock) {
        val didAcquire = lock.acquire()
        if (didAcquire) {
            log.info("This service has become the master responsible for handling Kubernetes events!")

            coroutineScope {
                var volcanoWatch = k8.client.watchResource<WatchEvent<VolcanoJob>>(
                    this,
                    KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
                )

                var podWatch = k8.client.watchResource<WatchEvent<Pod>>(
                    this,
                    KubernetesResources.pod.withNamespace(NAMESPACE_ANY),
                    mapOf("labelSelector" to "volcano.sh/job-name")
                )

                while (currentCoroutineContext().isActive) {
                    if (volcanoWatch.isClosedForReceive) {
                        log.warn("Stopped receiving new Volcano job watch events. Restarting watcher.")
                        volcanoWatch = k8.client.watchResource(this, KubernetesResources.volcanoJob)
                        continue
                    }

                    if (podWatch.isClosedForReceive) {
                        log.warn("Stopped receiving new Pod watch events. Restarting watcher.")
                        podWatch = k8.client.watchResource(this, KubernetesResources.pod)
                        continue
                    }

                    val duration = measureTime {
                        select<Unit> {
                            onTimeout(30_000) {
                                // Do nothing
                            }

                            volcanoWatch.onReceive {
                                log.info("Volcano job watch: $it")
                            }

                            podWatch.onReceive {
                                log.info("Pod job watch: $it")
                            }
                        }
                    }

                    if (duration >= 1.minutes) {
                        log.warn("Took too long to process events ($duration). We will probably lose master status.")
                    }

                    if (!lock.renew(90_000)) {
                        log.warn("Lock was lost. We are no longer the master. Did update take too long?")
                        break
                    }
                }

                runCatching { volcanoWatch.cancel() }
                runCatching { podWatch.cancel() }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
