package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.IPProtocol
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.defaultMapper
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStream

abstract class PodBasedContainer : Container {
    protected abstract val k8Client: KubernetesClient
    abstract val pod: Pod

    override val vCpuMillis: Int
        get() {
            val limit = pod.spec?.containers?.getOrNull(0)?.resources?.limits?.get("cpu")?.jsonPrimitive?.contentOrNull
                ?: "1000m"

            return limit.removeSuffix("m").toIntOrNull() ?: 1
        }

    override val memoryMegabytes: Int
        get() {
            val limit = pod.spec?.containers?.getOrNull(0)?.resources?.limits?.get("memory")?.jsonPrimitive
                ?.contentOrNull ?: "1Gi"

            return (memoryStringToBytes(limit) / (1024 * 1024)).toInt()
        }

    override val gpus: Int
        get() {
            val limit = pod.spec?.containers?.getOrNull(0)?.resources?.limits?.get("nvidia.com/gpu")
                ?.jsonPrimitive?.contentOrNull ?: "0"

            return limit.toInt()
        }


    private fun memoryStringToBytes(memory: String?): Long {
        val numbersOnly = Regex("[^0-9]")
        val ki = 1024
        val mi = 1048576
        val gi = 1073741824
        val ti = 1099511627776
        val pi = 1125899906842624

        return if (memory.isNullOrBlank()) {
            0
        } else (when {
            memory.contains("Ki") -> {
                numbersOnly.replace(memory, "").toLong() * ki
            }
            memory.contains("Mi") -> {
                numbersOnly.replace(memory, "").toLong() * mi
            }
            memory.contains("Gi") -> {
                numbersOnly.replace(memory, "").toLong() * gi
            }
            memory.contains("Ti") -> {
                numbersOnly.replace(memory, "").toLong() * ti
            }
            memory.contains("Pi") -> {
                numbersOnly.replace(memory, "").toLong() * pi
            }
            else -> {
                numbersOnly.replace(memory, "").toLong()
            }
        })
    }

    override suspend fun downloadLogs(out: OutputStream) {
        try {
            val podMeta = pod.metadata!!
            val podName = podMeta.name!!
            val namespace = podMeta.namespace!!

            runCatching {
                k8Client.sendRequest(
                    HttpMethod.Get,
                    KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                    mapOf("container" to USER_JOB_CONTAINER),
                    "log"
                ).execute { resp ->
                    val channel = resp.bodyAsChannel()
                    channel.copyTo(out)
                }
            }
        } finally {
            runCatching { out.close() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun watchLogs(scope: CoroutineScope): ReceiveChannel<String> {
        return scope.produce {
            coroutineScope {
                loop@ while (isActive && !isClosedForSend) {
                    // Guarantee that we don't spin too much. Unfortunately we don't have an API for selecting on the
                    // ByteReadChannel.
                    delay(50)

                    val readBuffer = ByteArray(1024 * 32)
                    val podMeta = pod.metadata!!
                    val podName = podMeta.name!!
                    val namespace = podMeta.namespace!!
                    k8Client.sendRequest(
                        HttpMethod.Get,
                        KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                        mapOf("follow" to "true", "container" to USER_JOB_CONTAINER),
                        "log"
                    ).execute { resp ->
                        val podChannel = resp.bodyAsChannel()

                        while (isActive && !podChannel.isClosedForRead) {
                            val read = podChannel.readAvailable(readBuffer)
                            if (read > 0) {
                                send(String(readBuffer, 0, read, Charsets.UTF_8))
                            } else if (read < 0) {
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun openShell(
        command: List<String>,
        tty: Boolean,
        stdin: Boolean,
        stderr: Boolean,
        stdout: Boolean,
        block: suspend ExecContext.() -> Unit
    ) {
        val pod = pod
        val metadata = pod.metadata!!
        k8Client.exec(
            KubernetesResourceLocator.common.pod.withNameAndNamespace(metadata.name!!, metadata.namespace!!),
            command, stdin, tty, stderr, stdout, block
        )
    }

    override val ipAddress: String get() = pod.status!!.podIP!!

    override val annotations: Map<String, String>
        get() {
            val annotationEntries = pod.metadata?.annotations?.entries ?: emptySet()
            return annotationEntries.associate { it.key to it.value.toString() }
        }

    override suspend fun upsertAnnotation(key: String, value: String) {
        val shouldInsert = key !in annotations
        val metadata = pod.metadata ?: error("no metadata")

        k8Client.patchResource(
            KubernetesResources.pod.withNameAndNamespace(
                metadata.name ?: error("no name"),
                metadata.namespace ?: error("no namespace")
            ),
            defaultMapper.encodeToString(
                ListSerializer(JsonObject.serializer()),
                // http://jsonpatch.com/
                listOf(
                    JsonObject(
                        mapOf(
                            // https://tools.ietf.org/html/rfc6901#section-3
                            "op" to JsonPrimitive(if (shouldInsert) "add" else "replace"),
                            "path" to JsonPrimitive("/metadata/annotations/${key.replace("/", "~1")}"),
                            "value" to JsonPrimitive(value)
                        )
                    )
                )
            ),
            ContentType("application", "json-patch+json")
        )
    }
}

abstract class PodBasedBuilder : ContainerBuilder {
    protected abstract val podSpec: Pod.Spec

    protected val container: Pod.Container get() = podSpec.containers!![0]
    protected val volumeMounts: ArrayList<Pod.Container.VolumeMount> get() = container.volumeMounts as ArrayList
    protected val volumes: ArrayList<Volume> get() = podSpec.volumes as ArrayList
    protected val ucloudVolume: Volume get() = volumes[0]

    protected fun initPodSpec() {
        val spec = podSpec
        spec.containers = listOf(
            Pod.Container(
                name = "user-job",
                imagePullPolicy = "IfNotPresent",
                volumeMounts = ArrayList(),
            )
        )

        spec.restartPolicy = "Never"
        spec.automountServiceAccountToken = false

        spec.volumes = arrayListOf(
            Volume(
                "ucloud",
                persistentVolumeClaim = Volume.PersistentVolumeClaimSource("cephfs", false)
            )
        )
    }

    override fun image(image: String) {
        container.image = image
    }

    override fun command(command: List<String>) {
        container.command = command
    }

    override fun environment(name: String, value: String) {
        container.env = container.env ?: ArrayList()

        val envVars = container.env as ArrayList
        envVars.add(Pod.EnvVar(name, value))
    }

    override fun mountUCloudFileSystem(subPath: String, containerPath: String, readOnly: Boolean) {
        volumeMounts.add(
            Pod.Container.VolumeMount(
                name = ucloudVolume.name,
                mountPath = containerPath,
                subPath = subPath,
                readOnly = readOnly
            )
        )
    }

    override fun mountSharedMemory(sharedMemorySizeMegabytes: Long) {
        val hasMountedBefore = volumeMounts.any { it.name == "shm" }
        if (!hasMountedBefore) {
            volumeMounts.add(
                Pod.Container.VolumeMount(
                    name = "shm",
                    mountPath = "/dev/shm"
                )
            )
        }

        if (hasMountedBefore) {
            val idx = volumes.indexOfFirst { it.name == "shm" }
            if (idx != -1) volumes.removeAt(idx)
        }

        volumes.add(
            Volume(
                name = "shm",
                emptyDir = Volume.EmptyDirVolumeSource(
                    medium = "Memory",
                    sizeLimit = "${sharedMemorySizeMegabytes}Mi"
                )
            )
        )
    }

    override fun mountSharedVolume(volumeName: String, containerPath: String) {
        if (!isSidecar) {
            volumes.add(
                Volume(
                    name = volumeName,
                    emptyDir = Volume.EmptyDirVolumeSource()
                )
            )
        }

        volumeMounts.add(
            Pod.Container.VolumeMount(
                name = volumeName,
                mountPath = containerPath
            )
        )
    }

    private var ipCounter = 0
    override fun mountIpAddress(ipAddress: String, networkInterface: String, ports: List<Pair<Int, IPProtocol>>) {
        val ipIdx = ipCounter++
        volumes.add(
            Volume(
                name = "ip$ipIdx",
                flexVolume = Volume.FlexVolumeSource(
                    driver = "ucloud/ipman",
                    fsType = "ext4",
                    options = JsonObject(
                        mapOf(
                            "addr" to JsonPrimitive(ipAddress),
                            "iface" to JsonPrimitive(networkInterface)
                        )
                    )
                )
            )
        )

        volumeMounts.add(
            Pod.Container.VolumeMount(
                name = "ip$ipIdx",
                readOnly = true,
                mountPath = "/mnt/.ucloud_ip$ipIdx"
            )
        )

        container.ports = container.ports ?: ArrayList()
        val cPorts = container.ports as ArrayList
        for ((port, protocol) in ports) {
            cPorts.add(
                Pod.Container.ContainerPort(
                    containerPort = port,
                    hostPort = port,
                    hostIP = ipAddress,
                    name = "pf${ipIdx}-$port",
                    protocol = when (protocol) {
                        IPProtocol.TCP -> "TCP"
                        IPProtocol.UDP -> "UDP"
                    }
                )
            )
        }
    }

    override var vCpuMillis: Int
        get() = error("Read not supported")
        set(value) = addResource("cpu", "${value}m")

    override var memoryMegabytes: Int
        get() = error("Read not supported")
        set(value) = addResource("memory", "${value}Mi")

    override var gpus: Int
        get() = error("Read not supported")
        set(value) = addResource("nvidia.com/gpu", "$value")

    private fun addResource(key: String, value: String) {
        val limits = container.resources?.limits?.toMutableMap() ?: HashMap()
        val requests = container.resources?.requests?.toMap()?.toMutableMap() ?: HashMap()

        limits[key] = JsonPrimitive(value)
        requests[key] = JsonPrimitive(value)

        container.resources = Pod.Container.ResourceRequirements(JsonObject(limits), JsonObject(requests))
    }

    override var shouldAllowRoot: Boolean
        get() = error("read not allowed")
        set(value) {
            container.securityContext = Pod.Container.SecurityContext(
                runAsNonRoot = !value,
                allowPrivilegeEscalation = value
            )
        }

    override var workingDirectory: String
        get() = error("read not allowed")
        set(value) {
            container.workingDir = value
        }
}
