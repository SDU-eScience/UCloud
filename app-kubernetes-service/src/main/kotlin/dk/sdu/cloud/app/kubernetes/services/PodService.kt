package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.service.BashEscaper
import io.fabric8.kubernetes.api.model.*
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ExecListener
import io.fabric8.kubernetes.client.dsl.ExecWatch
import io.fabric8.kubernetes.client.dsl.PodResource
import io.fabric8.kubernetes.client.dsl.internal.PodOperationsImpl
import kotlinx.coroutines.io.jvm.javaio.copyTo
import okhttp3.Response
import java.io.*
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch

class PodService(
    private val k8sClient: KubernetesClient
) {
    private fun podName(requestId: String): String = "job-$requestId"
    private val fileTransferContainer = "file-transfer"
    private val collectionContainer = "collection-job"
    private val temporaryStorage = "temporary-storage"
    private val namespace = "app-testing"

    fun create(/*request: VerifiedJob*/requestId: String) {
        // Create an init container which awaits file transfer
        // Files are transferred in submitFile. startContainer will trigger the init container to finish and start
        // the real container.

        // TODO Network policy. Should at least block all ingress and block egress to the entire container CIDR.

        val podName = podName(requestId)

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
                withInitContainers(
                    container {
                        withName(fileTransferContainer)
                        withImage("busybox:1.28")
                        withCommand(
                            listOf(
                                "sh",
                                "-c",
                                "until stat /tmp/file-transfer-done; do sleep 0.1; done;"
                            )
                        )
                        withVolumeMounts(
                            VolumeMount("/scratch", null, temporaryStorage, false, null)
                        )
                    },
                    container {
                        withName("user-job")
                        withImage("busybox:1.28")
                        withRestartPolicy("Never")
                        withCommand(listOf("sleep", "0.1"))
                        withVolumeMounts(
                            VolumeMount("/scratch", null, temporaryStorage, false, null)
                        )
                    }
                )

                withContainers(
                    container {
                        withName(collectionContainer)
                        withImage("busybox:1.28")
                        withRestartPolicy("Never")
                        withCommand(
                            listOf(
                                "sh",
                                "-c",
                                "sleep 120"
                            )
                        ) // This one should for us to have collected all files
                        withVolumeMounts(
                            VolumeMount("/scratch", null, temporaryStorage, false, null)
                        )
                    }
                )

                withVolumes(
                    volume {
                        withName(temporaryStorage)
                        withEmptyDir(EmptyDirVolumeSource())
                    }
                )
            }
            .done()

        awaitCatching(retries = 600, delay = 100) {
            val pod = k8sClient.pods().inNamespace(namespace).withName(podName).get()
            pod.status.initContainerStatuses.first().state.running != null
        }
    }

    suspend fun submitFile(
        requestId: String,
        relativePath: String,
        fileData: StreamingFile
    ) {
        val absolutePath = File("/scratch", relativePath).absolutePath

        println(listOf("sh -c", "cat - > ${BashEscaper.safeBashArgument(absolutePath)}"))

        val openLatch = CountDownLatch(1)
        val fileTransferChannel = k8sClient
            .pods()
            .inNamespace(namespace)
            .withName(podName(requestId))
            .inContainer(fileTransferContainer)
            .redirectingInput()
            .usingListener(object : ExecListener {
                override fun onOpen(response: Response?) {
                    openLatch.countDown()
                }

                override fun onFailure(t: Throwable?, response: Response?) {}
                override fun onClose(code: Int, reason: String?) {
                }
            })
            .exec("sh", "-c", "cat - > ${BashEscaper.safeBashArgument(absolutePath)}")

        fileTransferChannel.input
        openLatch.await()
        fileTransferChannel.use {
            fileData.channel.copyTo(it.input)
        }
    }

    fun startContainer(requestId: String) {
        lateinit var watch: Watch
        watch = k8sClient.pods().withName(podName(requestId)).watch(object : Watcher<Pod> {
            override fun onClose(cause: KubernetesClientException?) {
            }

            override fun eventReceived(action: Watcher.Action, resource: Pod) {
                val userContainer = resource.status.initContainerStatuses[1] ?: return
                if (userContainer.state.terminated != null) {
                    val startAt =
                        ZonedDateTime.parse(userContainer.state.terminated.startedAt).toInstant().toEpochMilli()
                    val finishedAt =
                        ZonedDateTime.parse(userContainer.state.terminated.finishedAt).toInstant().toEpochMilli()
                    println("App finished in ${finishedAt - startAt}ms")
                    watch.close()

                    collectFiles(listOf("*"), requestId)
                }
            }
        })

        k8sClient
            .pods()
            .inNamespace(namespace)
            .withName(podName(requestId))
            .inContainer(fileTransferContainer)
            .redirectingError()
            .exec("touch", "/tmp/file-transfer-done")
            .use { }
    }

    private fun collectFiles(globs: List<String>, requestId: String) {
        val podName = podName(requestId)
        awaitCatching(retries = 600, delay = 100) {
            val pod = k8sClient.pods().inNamespace(namespace).withName(podName).get()
            pod.status.containerStatuses.first().state.running != null
        }

        globs.forEach { glob ->
            println("Glob: $glob")
            val (output, _, _) = k8sClient.pods()
                .inNamespace(namespace)
                .withName(podName)
                .execWithDefaultListener(listOf("ls", "/scratch"))

            println("I will now write stuff: " + output!!.bufferedReader().readText())
            println("Done!")
       }
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

