package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.ShellRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.plugins.ComputePlugin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class K8Shell(
    private val k8: K8DependenciesImpl,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun ComputePlugin.ShellContext.handleShellSession(request: ShellRequest.Initialize) {
        val podName = k8.nameAllocator.jobIdAndRankToPodName(jobId, jobRank)
        val namespace = k8.nameAllocator.namespace()

        k8.client.exec(
            KubernetesResources.pod.withNameAndNamespace(podName, namespace),
            listOf(
                "sh", "-c",
                "TERM=xterm-256color; " +
                        "export TERM; " +
                        "([ -x /bin/bash ] && exec /bin/bash) || " +
                        "([ -x /usr/bin/bash ] && exec /usr/bin/bash) || " +
                        "([ -x /bin/zsh ] && exec /bin/zsh) || " +
                        "([ -x /usr/bin/zsh ] && exec /usr/bin/zsh) || " +
                        "([ -x /bin/fish ] && exec /bin/fish) || " +
                        "([ -x /usr/bin/fish ] && exec /usr/bin/fish) || " +
                        "exec /bin/sh"
            )
        ) {
            resizes.send(ExecResizeMessage(request.cols, request.rows))

            coroutineScope {
                launch {
                    outputs.consumeEach { f ->
                        emitData(f.bytes.toString(Charsets.UTF_8))
                    }
                }

                while (isActive() && !receiveChannel.isClosedForReceive) {
                    @Suppress("RemoveExplicitTypeArguments")
                    select<Unit> {
                        receiveChannel.onReceiveCatching {
                            val message = it.getOrNull() ?: return@onReceiveCatching
                            when (message) {
                                is ShellRequest.Initialize -> {
                                    throw RPCException(
                                        "Multiple initialize calls received",
                                        HttpStatusCode.BadRequest
                                    )
                                }

                                is ShellRequest.Input -> {
                                    stdin.send(message.data.toByteArray(Charsets.UTF_8))
                                }

                                is ShellRequest.Resize -> {
                                    resizes.send(ExecResizeMessage(message.cols, message.rows))
                                }
                            }
                        }

                        onTimeout(500) {
                            // Do nothing, just check if active
                        }
                    }
                }
            }
        }
    }
}
