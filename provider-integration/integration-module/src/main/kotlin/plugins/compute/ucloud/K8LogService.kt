package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files

/**
 * A service responsible for downloading and streaming of K8 logs from user jobs.
 */
class K8LogService(
    private val k8: K8Dependencies
) {
    data class LogMessage(val rank: Int, val message: String)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun useLogWatch(requestId: String): ReceiveChannel<LogMessage> {
        return k8.scope.produce {
            val jobName = k8.nameAllocator.jobIdToJobName(requestId)
            val namespace = k8.nameAllocator.namespace()

            val knownPods = HashSet<String>()
            var nextCheck = 0L

            coroutineScope {
                loop@ while (isActive && !isClosedForSend) {
                    // Guarantee that we don't spin too much. Unfortunately we don't have an API for selecting on the
                    // ByteReadChannel.
                    delay(50)

                    if (Time.now() > nextCheck) {
                        val pods = k8.client.listResources(
                            Pod.serializer(),
                            KubernetesResources.pod.withNamespace(namespace),
                            mapOf("labelSelector" to "$VOLCANO_JOB_NAME_LABEL=$jobName")
                        )

                        val activePodSet = pods.mapNotNull { pod ->
                            if (pod.status?.containerStatuses?.all { it.ready == true } != true) return@mapNotNull null
                            pod.metadata?.name
                        }.toSet()

                        val newPods = activePodSet - knownPods
                        val deletedPods = knownPods - activePodSet

                        deletedPods.forEach { knownPods.remove(it) }
                        knownPods.addAll(newPods)

                        newPods.forEach { podName ->
                            launch {
                                val readBuffer = ByteArray(1024 * 32)
                                k8.client.sendRequest(
                                    HttpMethod.Get,
                                    KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                                    mapOf("follow" to "true", "container" to USER_JOB_CONTAINER),
                                    "log"
                                ).execute { resp ->
                                    val podChannel = resp.bodyAsChannel()
                                    val rank = k8.nameAllocator.rankFromPodName(podName)

                                    while (isActive && !podChannel.isClosedForRead) {
                                        val read = podChannel.readAvailable(readBuffer)
                                        if (read > 0) {
                                            send(LogMessage(rank, String(readBuffer, 0, read, Charsets.UTF_8)))
                                        } else if (read < 0) {
                                            break
                                        }
                                    }
                                }
                            }
                        }

                        nextCheck = if (knownPods.isEmpty()) {
                            // Check often while there are no pods
                            Time.now() + 1_000
                        } else {
                            // Check less often while we have some pods
                            // TODO Ideally this would not trigger before all pods are ready
                            Time.now() + 60_000
                        }
                        continue@loop
                    }
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun downloadLogsToDirectory(jobId: String): File? {
        val directory = Files.createTempDirectory("logs").toFile()
        val jobName = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.namespace()

        val pods = try {
            k8.client.listResources(
                Pod.serializer(),
                KubernetesResources.pod.withNamespace(namespace),
                mapOf("labelSelector" to "$VOLCANO_JOB_NAME_LABEL=$jobName")
            )
        } catch (ex: KubernetesException) {
            if (ex.statusCode.value in setOf(400, 404)) {
                return null
            }
            throw ex
        }

        pods.forEach { p ->
            val podName = p.metadata?.name ?: return@forEach
            runCatching {
                k8.client.sendRequest(
                    HttpMethod.Get,
                    KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                    mapOf("container" to USER_JOB_CONTAINER),
                    "log"
                ).execute { resp ->
                    val channel = resp.bodyAsChannel()
                    val rank = k8.nameAllocator.rankFromPodName(podName)

                    val fileName = if (rank == 0) {
                        "stdout.txt"
                    } else {
                        "stdout-$rank.txt"
                    }

                    FileOutputStream(File(directory, fileName)).use { fos ->
                        channel.copyTo(fos)
                    }
                }
            }
        }

        return directory
    }

    companion object : Loggable {
        override val log = logger()
    }
}
