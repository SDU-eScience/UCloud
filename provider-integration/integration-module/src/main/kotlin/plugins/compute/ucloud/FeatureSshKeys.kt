package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.app.orchestrator.api.JobsControlBrowseSshKeys
import dk.sdu.cloud.app.store.api.SshDescription
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Time
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlin.coroutines.coroutineContext

object FeatureSshKeys : JobFeature {
    private const val successMarker = "ssh-key-installed-successfully"

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun JobManagement.onJobStart(rootJob: Container, children: List<Container>) {
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
            // TODO(Dan): Formalize this message in #3653
            if (!foundSuccess) {
                k8.addStatus(rootJob.jobId, "SSH: Failure! Unable to install keys. Try again later.")
            } else {
                k8.addStatus(rootJob.jobId, "SSH: Connected! Available at: ucloud@xxx.yyy.zzz.www -p ######")
            }
        }
    }
}
