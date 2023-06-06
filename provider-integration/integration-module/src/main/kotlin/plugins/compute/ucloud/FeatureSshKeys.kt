package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.IPProtocol
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsControlBrowseSshKeys
import dk.sdu.cloud.app.store.api.SshDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.config.ConfigSchema.Plugins.Jobs.UCloud.SshSubnet
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.plugins.storage.ucloud.LINUX_FS_USER_UID
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession

class FeatureSshKeys(
    private val subnets: List<SshSubnet>,
) : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        if (job.specification.sshEnabled != true) return
        if (subnets.isEmpty()) {
            k8.addStatus(job.id, "SSH: Failure! This provider does not support SSH servers.")
            return
        }

        val application = job.status.resolvedApplication!!
        val sshStatus = application.invocation.ssh ?: return
        if (sshStatus.mode == SshDescription.Mode.DISABLED) return

        val relevantKeys = JobsControl.browseSshKeys.call(
            JobsControlBrowseSshKeys(job.id),
            k8.serviceClient
        ).orNull()

        if (relevantKeys == null) {
            k8.addStatus(job.id, "SSH: Failure! Unable to install keys. Try again later.")
            return
        }

        builder.mountSharedVolume("ssh-keys", "/etc/ucloud/ssh")

        builder.sidecar("ssh-keys") {
            mountSharedVolume("ssh-keys", "/etc/ucloud/ssh")

            image("alpine:latest")
            vCpuMillis = 100
            memoryMegabytes = 64
            command(listOf(
                "/bin/sh",
                "-c",
                buildString {
                    appendLine("chmod 700 /etc/ucloud/ssh")
                    appendLine("touch /etc/ucloud/ssh/authorized_keys.ucloud")
                    appendLine("chmod 600 /etc/ucloud/ssh/authorized_keys.ucloud")
                    appendLine("cat >> /etc/ucloud/ssh/authorized_keys.ucloud << EOF")
                    relevantKeys.items.forEach { key ->
                        appendLine(key.specification.key.trim())
                    }
                    appendLine("EOF")

                    appendLine("chown ${LINUX_FS_USER_UID}:${LINUX_FS_USER_UID} -R /etc/ucloud/ssh")
                }
            ))
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
        //  Right now you need to supply a port range which will actually fall within the subnet you specify.
        val addr = cidr.first
        val a = (addr shr 24) and 0xFFu
        val b = (addr shr 16) and 0xFFu
        val c = 1 + (port / 254)
        val d = 1 + (port % 254)
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
                        on conflict (name, subnet, port) do nothing
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
}
