package org.esciencecloud.abc

import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.HostInfo
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.storage.Result
import java.util.concurrent.TimeUnit

class HPCStoreEndpoints(private val hostname: String, private val port: Int, private val rpc: RPCConfiguration) {
    private var server: KafkaRPCServer? = null
    private val hostInfo = HostInfo(hostname, port)

    private lateinit var streams: KafkaStreams

    private val slurmIdToInternalId = KafkaRPCEndpoint.simpleEndpoint<Long, String>(
            "/slurm", ApplicationStreamProcessor.TOPIC_SLURM_TO_JOB_ID
    ) { KafkaRPCEndpoint.resultFromNullable(it.toLongOrNull()) }

    private val jobToApp = KafkaRPCEndpoint.simpleEndpoint<String, HPCAppEvent.Pending>(
            "/job", ApplicationStreamProcessor.TOPIC_JOB_ID_TO_APP
    )

    @Suppress("UNCHECKED_CAST")
    private val endpoints = listOf(
            slurmIdToInternalId,
            jobToApp
    ) as List<KafkaRPCEndpoint<Any, Any>>

    fun start(streams: KafkaStreams, wait: Boolean = false) {
        if (server != null) throw IllegalStateException("RPC Server already started!")
        this.streams = streams

        val server = KafkaRPCServer(hostname, port, endpoints, streams, rpc.secretToken)
        server.start(wait = wait)
        this.server = server
    }

    suspend fun querySlurmIdToInternal(slurmId: Long): Result<String> =
            slurmIdToInternalId.query(streams, hostInfo, slurmId, rpc.secretToken)

    suspend fun queryJobIdToApp(jobId: String): Result<HPCAppEvent.Pending> =
            jobToApp.query(streams, hostInfo, jobId, rpc.secretToken)

    fun stop() {
        server!!.stop(0, 5, TimeUnit.SECONDS)
    }
}
