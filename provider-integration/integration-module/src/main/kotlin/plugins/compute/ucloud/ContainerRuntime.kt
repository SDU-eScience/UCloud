package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.IPProtocol
import dk.sdu.cloud.app.orchestrator.api.JobState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.OutputStream

interface Container {
    val jobId: String
    val rank: Int
    val state: JobState
    val annotations: Map<String, String>
    val ipAddress: String

    suspend fun upsertAnnotation(key: String, value: String)

    suspend fun cancel()

    suspend fun downloadLogs(out: OutputStream)
    suspend fun watchLogs(scope: CoroutineScope): ReceiveChannel<String>

    suspend fun openShell(
        command: List<String>,
        tty: Boolean = true,
        stdin: Boolean = true,
        stderr: Boolean = true,
        stdout: Boolean = true,
        block: suspend ExecContext.() -> Unit,
    )

    suspend fun allowNetworkTo(jobId: String, rank: Int?)
    suspend fun allowNetworkFrom(jobId: String, rank: Int?)
}

interface ContainerRuntime<C : Container, B : ContainerBuilder<B>> {
    fun builder(jobId: String, replicas: Int, block: B.() -> Unit = {}): B

    suspend fun scheduleGroup(group: List<B>)
    suspend fun retrieve(jobId: String, rank: Int): C?
    suspend fun list(): List<C>
}

interface ContainerBuilder<B : ContainerBuilder<B>> {
    val jobId: String
    val replicas: Int

    var shouldAllowRoot: Boolean
    var workingDirectory: String

    var productCategoryRequired: String?
    var vCpuMillis: Int
    var memoryMegabytes: Int
    var gpus: Int

    val isSidecar: Boolean
    fun supportsSidecar(): Boolean
    fun sidecar(builder: B.() -> Unit)
    val sidecars: List<B>

    fun image(image: String)
    fun environment(name: String, value: String)
    fun command(command: List<String>)

    fun mountUCloudFileSystem(subPath: String, containerPath: String, readOnly: Boolean)
    fun mountSharedMemory(sharedMemorySizeMegabytes: Long)

    fun mountIpAddressToEnvVariable(variableName: String) {}
    fun mountIpAddress(ipAddress: String, networkInterface: String, ports: List<Pair<Int, IPProtocol>>)

    fun allowNetworkTo(jobId: String, rank: Int?)
    fun allowNetworkFrom(jobId: String, rank: Int?)
    fun hostAlias(jobId: String, rank: Int, alias: String)
}
