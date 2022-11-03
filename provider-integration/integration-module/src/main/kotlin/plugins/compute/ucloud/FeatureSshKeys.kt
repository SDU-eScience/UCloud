package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.IPProtocol
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsControlBrowseSshKeys
import dk.sdu.cloud.app.store.api.SshDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.config.ConfigSchema.Plugins.Jobs.UCloud.SshSubnet
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlin.coroutines.coroutineContext

class FeatureSshKeys(
    private val subnets: List<SshSubnet>,
) : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        if (job.specification.sshEnabled != true) return
        if (subnets.isEmpty()) {
            k8.addStatus(job.id, "SSH: Failure! This provider does not support SSH servers.")
            return
        }

        val portAndSubnet = findAndRegisterPort(job.id)
        val ip = mapPort(portAndSubnet.subnet.privateCidr, portAndSubnet.port)
        builder.mountIpAddress(ip, portAndSubnet.subnet.iface, listOf(Pair(22, IPProtocol.TCP)))

        k8.addStatus(
            job.id,
            buildString {
                // NOTE(Dan): This message will require this specific format as the frontend will parse it.
                append("SSH: ")
                append("Connected! ")
                append("Available at: ")
                append("ssh ucloud@")
                append(portAndSubnet.subnet.publicHostname)
                append(" -p ")
                append(portAndSubnet.port)
            }
        )
    }

    private fun mapPort(subnet: String, port: Int): String {
        val cidr = IpUtils.validateCidr(subnet)

        // TODO(Dan): Robustness would be improved be if we checked to make sure that this actually makes sense.
        //  Right now you need to supply a port range which will actually full within the subnet you specify.
        val addr = cidr.first
        val a = (addr shr 24) and 0xFFu
        val b = (addr shr 16) and 0xFFu
        val c = (1 + port) / 254
        val d = (1 + port) % 254
        return "$a.$b.$c.$d"
    }

    override suspend fun JobManagement.onCleanup(jobId: String) {
        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    delete from ucloud_compute_bound_ssh_ports
                    where job_id = :job_id
                """
            ).useAndInvokeAndDiscard(
                prepare = {
                    bindString("job_id", jobId)
                }
            )
        }
    }

    private data class ConsumedPort(
        val subnet: SshSubnet,
        val port: Int,
    )

    private suspend fun JobManagement.findAndRegisterPort(jobId: String): ConsumedPort {
        return dbConnection.withSession { session ->
            var randomSubnet: SshSubnet? = null
            var randomPort: Int? = null
            var success = false
            var attempts = 0

            while (!success && attempts < 10_000) {
                randomSubnet = subnets.random()
                randomPort = ((randomSubnet.portMin)..(randomSubnet.portMax)).random()

                session.prepareStatement(
                    //language=postgresql
                    """
                        insert into ucloud_compute_bound_ssh_ports (name, subnet, port, job_id)
                        values (:plugin_name, :subnet, :port, :job_id)
                        on conflict (name, subnet, port, job_id) do nothing
                        returning port
                    """
                ).useAndInvoke(
                    prepare = {
                        bindString("plugin_name", pluginName)
                        bindString("subnet", randomSubnet.privateCidr)
                        bindInt("port", randomPort)
                        bindString("job_id", jobId)
                    },
                    readRow = { row ->
                        success = true
                    }
                )

                attempts++
            }

            if (!success) throw RPCException("Could not allocate a port for SSH", HttpStatusCode.InternalServerError)
            ConsumedPort(randomSubnet!!, randomPort!!)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun JobManagement.onJobStart(rootJob: Container, children: List<Container>) {
        if (subnets.isEmpty()) return
        val cachedJob = k8.jobCache.findJob(rootJob.jobId) ?: return
        if (cachedJob.specification.sshEnabled != true) return
        val application = cachedJob.status.resolvedApplication!!
        val sshStatus = application.invocation.ssh ?: return
        if (sshStatus.mode == SshDescription.Mode.DISABLED) return

        val relevantKeys = JobsControl.browseSshKeys.call(
            JobsControlBrowseSshKeys(rootJob.jobId),
            k8.serviceClient
        ).orThrow()

        // NOTE(Dan): We only mount the keys in the rootJob right now. It might be better if we connect all of them.
        rootJob.openShell(listOf("/usr/bin/sh")) {
            stdin.send(buildString {
                appendLine("mkdir -p /home/ucloud/.ssh")
                appendLine("chmod 700 /home/ucloud/.ssh")
                appendLine("touch /home/ucloud/.ssh/authorized_keys")
                appendLine("chmod 600 /home/ucloud/.ssh/authorized_keys")
                appendLine("cat > /home/ucloud/.ssh/authorized_keys << EOF")
                relevantKeys.items.forEach { key ->
                    appendLine(key.specification.key.trim())
                }
                appendLine("EOF")
                appendLine("echo $successMarker")
            }.encodeToByteArray())

            var foundSuccess = false
            val deadline = Time.now() + 10_000
            while (coroutineContext.isActive && Time.now() < deadline && !foundSuccess) {
                select<Unit> {
                    outputs.onReceiveCatching { message ->
                        val decoded = message.getOrNull()?.bytes?.decodeToString() ?: ""
                        foundSuccess = decoded.contains(successMarker) && !decoded.contains("echo")
                    }

                    onTimeout(500) {
                        // Do nothing
                    }
                }
            }

            // NOTE(Dan): This message will require this specific format as the frontend will parse it.
            if (!foundSuccess) {
                k8.addStatus(rootJob.jobId, "SSH: Failure! Unable to install keys. Try again later.")
            }
        }
    }

    companion object {
        private const val successMarker = "ssh-key-installed-successfully"
    }
}
