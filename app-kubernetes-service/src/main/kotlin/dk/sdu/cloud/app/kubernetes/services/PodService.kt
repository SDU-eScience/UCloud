package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.PodResource
import io.fabric8.kubernetes.client.dsl.internal.PodOperationsImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.Response
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch

class PodService(
    private val k8sClient: KubernetesClient,
    private val serviceClient: AuthenticatedClient
) {
    private fun podName(requestId: String): String = "job-$requestId"
    private val dataDirectory = "/data"
    private val localDirectory = "/scratch"
    private val temporaryStorage = "temporary-storage"
    private val dataStorage = "workspace-storage"
    private val namespace = "app-testing"

    fun create(verifiedJob: VerifiedJob) {
        // TODO Network policy. Should at least block all ingress and block egress to the entire container CIDR.

        val podName = podName(verifiedJob.id)

        log.info("Creating new job with name: $podName")

        k8sClient.pods().createNew()
            .metadata {
                withName(podName)
                withNamespace(this@PodService.namespace)

                withLabels(
                    mapOf(
                        "role" to "sducloud-app"
                    )
                )
            }
            .spec {
                withContainers(
                    container {
                        val app = verifiedJob.application.invocation
                        val tool = verifiedJob.application.invocation.tool.tool!!.description
                        val givenParameters = verifiedJob.jobInput.asMap().mapNotNull { (paramName, value) ->
                            if (value != null) {
                                app.parameters.find { it.name == paramName }!! to value
                            } else {
                                null
                            }
                        }.toMap()

                        val command = app.invocation.flatMap { it.buildInvocationList(givenParameters) }

                        log.debug("Container is: ${tool.container}")
                        log.debug("Executing command: $command")

                        withName("user-job")
                        withImage(tool.container)
                        withRestartPolicy("Never")
                        withCommand(command)
                        withSecurityContext(
                            PodSecurityContext(
                                verifiedJob.ownerUid,
                                verifiedJob.ownerUid,
                                true,
                                verifiedJob.ownerUid,
                                null,
                                null,
                                null
                            )
                        )

                        withWorkingDir(dataDirectory)

                        withVolumeMounts(
                            VolumeMount(localDirectory, null, temporaryStorage, false, null),
                            VolumeMount(
                                dataDirectory,
                                null,
                                dataStorage,
                                false,
                                verifiedJob.workspace?.removePrefix("/") ?: throw RPCException(
                                    "No workspace found",
                                    HttpStatusCode.BadRequest
                                )
                            )
                        )
                    }
                )

                withVolumes(
                    volume {
                        withName(temporaryStorage)
                        withEmptyDir(EmptyDirVolumeSource())
                    },

                    volume {
                        withName(dataStorage)
                        withPersistentVolumeClaim(PersistentVolumeClaimVolumeSource("cephfs", false))
                    }
                )
            }
            .done()

        GlobalScope.launch {
            log.info("Awaiting container start!")
            awaitCatching(retries = 600, delay = 100) {
                val pod = k8sClient.pods().inNamespace(namespace).withName(podName).get()
                val state = pod.status.containerStatuses.first().state
                state.running != null || state.terminated != null
            }


            launch {
                ComputationCallbackDescriptions.requestStateChange.call(
                    StateChangeRequest(verifiedJob.id, JobState.RUNNING),
                    serviceClient
                )
            }

            k8sClient.pods().inNamespace(namespace).withName(podName).watchLog(System.out)

            log.info("Attaching watch event!")
            lateinit var watch: Watch
            watch = k8sClient.pods().inNamespace(namespace).withName(podName).watch(object : Watcher<Pod> {
                override fun onClose(cause: KubernetesClientException?) {
                }

                override fun eventReceived(action: Watcher.Action, resource: Pod) {
                    log.debug("Received event: $action")
                    val userContainer = resource.status.containerStatuses[0] ?: return
                    if (userContainer.state.terminated != null) {
                        val startAt =
                            ZonedDateTime.parse(userContainer.state.terminated.startedAt).toInstant().toEpochMilli()
                        val finishedAt =
                            ZonedDateTime.parse(userContainer.state.terminated.finishedAt).toInstant().toEpochMilli()
                        log.info("App finished in ${finishedAt - startAt}ms")

                        log.info("Final log:")
                        log.info("\n" + k8sClient.pods().inNamespace(namespace).withName(podName).log)

                        log.info("Got log. This is no longer listening for a log.")
                        val duration = run {
                            val duration = Duration.ofMillis(finishedAt - startAt)
                            val days = duration.toDaysPart()
                            val hours = duration.toHoursPart()
                            val minutes = duration.toMinutesPart()
                            val seconds = duration.toSecondsPart()
                            SimpleDuration((days * 24).toInt() + hours, minutes, seconds)
                        }
                        log.info("Simple duration $duration")

                        GlobalScope.launch {
                            log.info("Calling completed")
                            ComputationCallbackDescriptions.completed.call(
                                JobCompletedRequest(verifiedJob.id, duration, true),
                                serviceClient
                            ).orThrow()
                        }

                        log.info("Closing watch")
                        watch.close()
                    }
                }
            })
        }
    }

    fun cleanup(requestId: String) {
        val pod = podName(requestId)
        k8sClient.pods().inNamespace(namespace).withName(pod).delete()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

data class RemoteProcess(
    val stdout: InputStream?,
    val stderr: InputStream?,
    val stdin: OutputStream?
)

class OutputStreamWithCustomClose(
    private val delegate: OutputStream,
    private val onClose: () -> Unit
) : OutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
    }

    override fun write(b: ByteArray?) {
        delegate.write(b)
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        delegate.write(b, off, len)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
        onClose()
    }
}

fun PodResource<Pod, DoneablePod>.execWithDefaultListener(
    command: List<String>,
    attachStdout: Boolean = true,
    attachStderr: Boolean = false,
    attachStdin: Boolean = false
): RemoteProcess {
    // I don't have a clue on how you are supposed to call this API.
    lateinit var watch: ExecWatch

    val latch = CountDownLatch(1)

    var pipeErrIn: InputStream? = null
    var pipeErrOut: OutputStream? = null

    var pipeOutIn: InputStream? = null
    var pipeOutOut: OutputStream? = null

    var pipeInOut: OutputStream? = null

    var execable: PodResource<Pod, DoneablePod> = this
    if (attachStdout) {
        pipeOutOut = PipedOutputStream()
        pipeOutIn = PipedInputStream(pipeOutOut)
        execable = execable.writingOutput(pipeOutOut) as PodOperationsImpl
    }

    if (attachStderr) {
        pipeErrOut = PipedOutputStream()
        pipeErrIn = PipedInputStream(pipeErrOut)
        execable = execable.writingError(pipeErrOut) as PodOperationsImpl
    }

    if (attachStdin) {
        pipeInOut = PipedOutputStream()
        val pipeInIn = PipedInputStream(pipeInOut)
        execable = execable.readingInput(pipeInIn) as PodOperationsImpl
    }

    execable
        .usingListener(object : ExecListener {
            override fun onOpen(response: Response?) {
                println("Open!")
                latch.countDown()
            }

            override fun onFailure(t: Throwable?, response: Response?) {
            }

            override fun onClose(code: Int, reason: String?) {
                println("Closing!")
                pipeErrOut?.close()
                pipeOutOut?.close()
                pipeInOut?.close()
                watch.close()
            }
        })
        .exec(*command.toTypedArray()).also { watch = it }

    latch.await()
    return RemoteProcess(
        pipeOutIn,
        pipeErrIn,
        pipeInOut?.let {
            OutputStreamWithCustomClose(it) {
                // We need to wait for the data to reach the other end. Without this sleep we will close before
                // the data is even sent to the pod.
                Thread.sleep(250)

                // We need to signal the other end that we are done. We do this by closing the watch.
                watch.close()
            }
        }
    )
}

fun DoneablePod.metadata(builder: ObjectMetaBuilder.() -> Unit): DoneablePod {
    val objectMetaBuilder = ObjectMetaBuilder()
    objectMetaBuilder.builder()
    withMetadata(objectMetaBuilder.build())
    return this
}

fun DoneablePod.spec(builder: PodSpecBuilder.() -> Unit): DoneablePod {
    val podBuilder = PodSpecBuilder()
    podBuilder.builder()
    withSpec(podBuilder.build())
    return this
}

fun PodSpecBuilder.container(builder: ContainerBuilder.() -> Unit): Container {
    val containerBuilder = ContainerBuilder()
    containerBuilder.builder()
    return containerBuilder.build()
}

fun PodSpecBuilder.volume(builder: VolumeBuilder.() -> Unit): Volume {
    val volumeBuilder = VolumeBuilder()
    volumeBuilder.builder()
    return volumeBuilder.build()
}

fun await(retries: Int = 50, delay: Long = 100, condition: () -> Boolean) {
    for (attempt in 0 until retries) {
        if (condition()) return
        Thread.sleep(delay)
    }

    throw IllegalStateException("Condition failed!")
}

fun awaitCatching(retries: Int = 50, delay: Long = 100, condition: () -> Boolean) {
    for (attempt in 0 until retries) {
        if (runCatching(condition).getOrNull() == true) return
        Thread.sleep(delay)
    }

    throw IllegalStateException("Condition failed!")
}

