package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.calls.types.StreamingFile
import dk.sdu.cloud.service.BashEscaper
import io.fabric8.kubernetes.api.model.Container
import io.fabric8.kubernetes.api.model.ContainerBuilder
import io.fabric8.kubernetes.api.model.DoneablePod
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodSpecBuilder
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeBuilder
import io.fabric8.kubernetes.api.model.VolumeMount
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watch
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.kubernetes.client.dsl.ExecListener
import kotlinx.coroutines.io.jvm.javaio.copyTo
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PipedInputStream
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
            .use {  }
    }

    private fun collectFiles(globs: List<String>, requestId: String) {
        val podName = podName(requestId)
        awaitCatching(retries = 600, delay = 100) {
            val pod = k8sClient.pods().inNamespace(namespace).withName(podName).get()
            pod.status.containerStatuses.first().state.running != null
        }

        globs.forEach { glob ->
            println("Glob: $glob")
            val bos = ByteArrayOutputStream()
            PipedInputStream()
//            val latch = CountDownLatch(1)
            var closed = false
            PipedInputStream()
            k8sClient.pods()
                .inNamespace(namespace)
                .withName(podName)
                .redirectingOutput()
                .usingListener(object : ExecListener {
                    override fun onOpen(response: Response?) {
//                        latch.countDown()
                    }

                    override fun onFailure(t: Throwable?, response: Response?) {
                    }

                    override fun onClose(code: Int, reason: String?) {
                        closed = true
                    }
                })
                .exec("ls", "/scratch")
                .use { exec ->
                    println(exec.output)
                    val stream = (exec.output as PipedInputStream)
                    val bytes = ArrayList<Byte>()
                    while (!closed) {
                        val byte = stream.read()
                        println("Read something! $byte")
                        if (byte != -1) {
                            bytes.add(byte.toByte())
                        }
                    }

                    println(bytes.toTypedArray().toByteArray().toString(Charsets.UTF_8))
                    println("Done")
                }

//            latch.await()
//            println(bos.toByteArray().toString(Charsets.UTF_8))
//            println(exec.output.bufferedReader().readText())
        }
    }

    private fun runCommand() {

    }
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

