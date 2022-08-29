package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.select

/**
 * A service responsible for downloading and streaming of K8 logs from user jobs.
 */
class K8LogService(
    private val k8: K8Dependencies,
    private val runtime: ContainerRuntime,
) {
    data class LogMessage(val rank: Int, val message: String)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun useLogWatch(requestId: String): ReceiveChannel<LogMessage> {
        return k8.scope.produce {
            val job = k8.jobCache.findJob(requestId) ?: return@produce

            val knownRanks = HashMap<Int, ReceiveChannel<String>>()
            coroutineScope {
                loop@ while (isActive && !isClosedForSend) {
                    select {
                        knownRanks.forEach { rank, channel ->
                            channel.onReceiveCatching { result ->
                                val message = result.getOrNull()
                                if (message == null) {
                                    knownRanks.remove(rank)
                                } else {
                                    send(LogMessage(rank, message))
                                }
                            }
                        }

                        onTimeout(1000) {
                            for (rank in 0 until job.specification.replicas) {
                                if (rank in knownRanks) continue
                                val replica = runtime.retrieve(job.id, rank) ?: continue
                                knownRanks[rank] = replica.watchLogs(this@coroutineScope)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
