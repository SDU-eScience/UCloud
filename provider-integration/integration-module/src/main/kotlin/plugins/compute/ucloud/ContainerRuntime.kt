package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.CpuAndMemory
import dk.sdu.cloud.app.orchestrator.api.IPProtocol
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.storage.ucloud.FsSystem
import dk.sdu.cloud.utils.LinuxOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.OutputStream

interface Container {
    val jobId: String
    val rank: Int
    val annotations: Map<String, String>

    suspend fun upsertAnnotation(key: String, value: String)

    suspend fun cancel(force: Boolean = false)

    suspend fun downloadLogs(out: LinuxOutputStream)
    suspend fun watchLogs(scope: CoroutineScope): ReceiveChannel<String>

    suspend fun openShell(
        command: List<String>,
        tty: Boolean = true,
        stdin: Boolean = true,
        stderr: Boolean = true,
        stdout: Boolean = true,
        block: suspend ExecContext.() -> Unit,
    )

    suspend fun allowNetworkTo(jobId: String, rank: Int? = null)
    suspend fun allowNetworkFrom(jobId: String, rank: Int? = null)

    fun stateAndMessage(): Pair<JobState, String>
    suspend fun productCategory(): String?
    suspend fun mountedDirectories(): List<UCloudMount>

    val vCpuMillis: Int
    val memoryMegabytes: Int
    val gpus: Int
}

data class UCloudMount(
    val systemName: String,
    val subpath: String,
)

interface ComputeNode {
    suspend fun productCategory(): String?
    suspend fun retrieveCapacity(): CpuAndMemory
}

interface ContainerRuntime {
    fun builder(jobId: String, replicas: Int, block: ContainerBuilder.() -> Unit = {}): ContainerBuilder

    suspend fun schedule(container: ContainerBuilder)
    suspend fun retrieve(jobId: String, rank: Int): Container?
    suspend fun list(): List<Container>
    suspend fun listNodes(): List<ComputeNode>
    suspend fun openTunnel(jobId: String, rank: Int, port: Int): Tunnel

    /**
     * Removes a job from the queue, if it is present in the queue. This does nothing if the job is either running or
     * not in the queue at all. There is no guarantee that parts of the job could be in the queue and running at the
     * same time.
     */
    suspend fun removeJobFromQueue(jobId: String) {
        // Do nothing
    }

    suspend fun isJobKnown(jobId: String): Boolean {
        return retrieve(jobId, 0) != null
    }

    fun requiresReschedulingOfInQueueJobsOnStartup(): Boolean = false
    fun notifyReschedulingComplete() {}
}

data class Tunnel(val hostnameOrIpAddress: String, val port: Int, val close: suspend () -> Unit)

interface ContainerBuilder {
    val jobId: String
    val replicas: Int

    var shouldAllowRoot: Boolean
    var workingDirectory: String

    var runtime: String?

    var productCategoryRequired: String?
    var vCpuMillis: Int
    var memoryMegabytes: Int
    var gpus: Int

    val isSidecar: Boolean
    fun supportsSidecar(): Boolean
    fun sidecar(name: String, builder: ContainerBuilder.() -> Unit)

    fun image(image: String)
    fun environment(name: String, value: String)
    fun command(command: List<String>)

    fun mountUCloudFileSystem(system: FsSystem, subPath: String, containerPath: String, readOnly: Boolean)
    fun mountSharedMemory(sharedMemorySizeMegabytes: Long)
    fun mountSharedVolume(volumeName: String, containerPath: String)

    fun mountIpAddressToEnvVariable(variableName: String) {}
    fun mountIpAddress(ipAddress: String, networkInterface: String, ports: List<Pair<Int, IPProtocol>>)

    fun allowNetworkTo(jobId: String, rank: Int? = null)
    fun allowNetworkFrom(jobId: String, rank: Int? = null)
    fun allowNetworkFromSubnet(subnet: String)
    fun allowNetworkToSubnet(subnet: String)
    fun hostAlias(jobId: String, rank: Int, alias: String)

    fun upsertAnnotation(key: String, value: String)
}
