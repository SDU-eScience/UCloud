package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.IPProtocol
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.utils.LinuxOutputStream
import dk.sdu.cloud.utils.copyTo
import dk.sdu.cloud.plugins.storage.ucloud.FsSystem
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
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
import kotlin.math.roundToInt

abstract class PodBasedContainer : Container {
    protected abstract val k8Client: KubernetesClient
    abstract val pod: Pod

    override val vCpuMillis: Int
        get() {
            val limit = pod.spec?.containers?.getOrNull(0)?.resources?.limits?.get("cpu")?.jsonPrimitive?.contentOrNull
                ?: "1000m"

            return (cpuStringToCores(limit) * 1000).roundToInt()
        }

    override val memoryMegabytes: Int
        get() {
            val limit = pod.spec?.containers?.getOrNull(0)?.resources?.limits?.get("memory")?.jsonPrimitive
                ?.contentOrNull ?: "1Gi"

            return (memoryStringToBytes(limit) / (1000 * 1000)).toInt()
        }

    override val gpus: Int
        get() {
            val limit = pod.spec?.containers?.getOrNull(0)?.resources?.limits?.get("nvidia.com/gpu")
                ?.jsonPrimitive?.contentOrNull ?: "0"

            return limit.toInt()
        }

    private fun cpuStringToCores(cpus: String?): Double {
        val numbersOnly = Regex("[^0-9]")

        return if (cpus.isNullOrBlank()) {
            0.toDouble()
        } else (when {
            cpus.contains("m") -> {
                numbersOnly.replace(cpus, "").toDouble() / 1000
            }
            else -> {
                numbersOnly.replace(cpus, "").toDouble()
            }
        })
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

    override suspend fun downloadLogs(out: LinuxOutputStream) {
        val podMeta = pod.metadata!!
        val podName = podMeta.name!!
        val namespace = podMeta.namespace!!

        runCatching {
            k8Client.sendRequest(
                HttpMethod.Get,
                KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                mapOf("container" to USER_JOB_CONTAINER),
                "log",
                longRunning = true,
            ).execute { resp ->
                val channel = resp.bodyAsChannel()
                channel.copyTo(out)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun watchLogs(scope: CoroutineScope): ReceiveChannel<String> {
        return scope.produce {
            coroutineScope {
                val readBuffer = ByteArray(1024 * 32)
                val podMeta = pod.metadata!!
                val podName = podMeta.name!!
                val namespace = podMeta.namespace!!

                loop@ while (isActive && !isClosedForSend) {
                    // Guarantee that we don't spin too much. Unfortunately we don't have an API for selecting on the
                    // ByteReadChannel.
                    delay(50)

                    k8Client.sendRequest(
                        HttpMethod.Get,
                        KubernetesResources.pod.withNameAndNamespace(podName, namespace),
                        mapOf("follow" to "true", "container" to USER_JOB_CONTAINER),
                        "log",
                        longRunning = true,
                    ).execute { resp ->
                        if (!resp.status.isSuccess()) return@execute

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

    val ipAddress: String get() = pod.status!!.podIP!!

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

    override suspend fun mountedDirectories(): List<UCloudMount> {
        val container = pod.spec?.containers?.first() ?: return emptyList()
        val volumeMounts = container.volumeMounts ?: emptyList()
        return volumeMounts.mapNotNull { mount ->
            var systemName = mount.name ?: return@mapNotNull null
            if (systemName == "ucloud") systemName = "CephFS"
            val pathName = mount.subPath ?: return@mapNotNull null
            if (systemName == "shm") return@mapNotNull null
            if (systemName.startsWith("module-")) return@mapNotNull null
            UCloudMount(systemName, pathName)
        }
    }
}

abstract class PodBasedBuilder : ContainerBuilder {
    abstract val podSpec: Pod.Spec

    val container: Pod.Container get() = podSpec.containers!![0]
    val volumeMounts: ArrayList<Pod.Container.VolumeMount> get() = container.volumeMounts as ArrayList
    val volumes: ArrayList<Volume> get() = podSpec.volumes as ArrayList

    // NOTE(Dan): These are only normalized by the Pod2Runtime at the moment. This is not great, but we are likely
    // deprecating and removing the two other runtimes.
    val rootOnlyVolumes = ArrayList<Volume>()
    val rootOnlyVolumeMounts = ArrayList<Pod.Container.VolumeMount>()
    val rootOnlyPorts = ArrayList<Pod.Container.ContainerPort>()

    protected abstract val fakeIpMount: Boolean

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

        spec.volumes = arrayListOf()
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

    override fun mountUCloudFileSystem(system: FsSystem, subPath: String, containerPath: String, readOnly: Boolean) {
        volumeMounts.add(
            Pod.Container.VolumeMount(
                name = mountFsSystemIfNeeded(system),
                mountPath = containerPath,
                subPath = subPath,
                readOnly = readOnly
            )
        )
    }

    private val mountedSystems = HashSet<String>()
    private fun mountFsSystemIfNeeded(system: FsSystem): String {
        val normalizedName = system.name.lowercase()
        if (normalizedName in mountedSystems) return normalizedName
        mountedSystems.add(normalizedName)

        val volume = when {
            system.volumeClaim != null -> {
                Volume(
                    normalizedName,
                    persistentVolumeClaim = Volume.PersistentVolumeClaimSource(system.volumeClaim, false)
                )
            }

            system.hostPath != null -> {
                Volume(
                    normalizedName,
                    hostPath = Volume.HostPathSource(system.hostPath, "Directory")
                )
            }

            else -> error("Bad configuration supplied. Unable to mount system: $system")
        }

        volumes.add(volume)
        return normalizedName
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
                    sizeLimit = "${sharedMemorySizeMegabytes}M"
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
        if (fakeIpMount) return
        val ipIdx = ipCounter++
        rootOnlyVolumes.add(
            Volume(
                name = "ip$ipIdx",
                csi = Volume.CsiVolumeSource(
                    driver = "ucloud.csi.ipfs",
                    volumeAttributes = JsonObject(
                        mapOf(
                            // NOTE(Dan): /16 is required for our new infrastructure. We might need to move this into
                            // some sort of configuration later.
                            "address" to JsonPrimitive("$ipAddress/16"),
                            "interface" to JsonPrimitive(networkInterface)
                        )
                    )
                )
            )
        )

        rootOnlyVolumeMounts.add(
            Pod.Container.VolumeMount(
                name = "ip$ipIdx",
                readOnly = true,
                mountPath = "/mnt/.ucloud_ip$ipIdx"
            )
        )

        for ((port, protocol) in ports) {
            rootOnlyPorts.add(
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

    private var vCpuMillisField: Int = 0
    override var vCpuMillis: Int
        get() = vCpuMillisField
        set(value) {
            vCpuMillisField = value
            addResource("cpu", "${value}m")
        }

    private var memoryMegabytesField: Int = 0
    override var memoryMegabytes: Int
        get() = memoryMegabytesField
        set(value) {
            memoryMegabytesField = value
            addResource("memory", "${value}M")
        }

    private var gpusField: Int = 0
    override var gpus: Int
        get() = gpusField
        set(value) {
            gpusField = value
            addResource("nvidia.com/gpu", "$value")
        }

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

    override var runtime: String?
        get() = error("read not allowed")
        set(value) {
            podSpec.runtimeClassName = value
        }
}
