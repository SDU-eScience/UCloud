package org.esciencecloud.abc

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_HPC_APP_EVENTS
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_HPC_APP_REQUESTS
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_JOB_ID_TO_APP
import org.esciencecloud.abc.ApplicationStreamProcessor.Companion.TOPIC_SLURM_TO_JOB_ID
import org.esciencecloud.abc.api.HPCAppEvent
import org.esciencecloud.abc.api.HPCAppRequest
import org.esciencecloud.abc.processors.StartProcessor
import org.esciencecloud.abc.ssh.SSHConnectionPool
import org.esciencecloud.abc.ssh.SimpleSSHConfig
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class HPCStreamProcessor(
        private val storageConnectionFactory: StorageConnectionFactory,
        private val sBatchGenerator: SBatchGenerator,
        private val sshConfig: SimpleSSHConfig,
        private val sshConnectionPool: SSHConnectionPool,
        private val slurmPollAgent: SlurmPollAgent
) {
    private val log = LoggerFactory.getLogger(HPCStreamProcessor::class.java)

    // TODO Should this component really be creating these?
    private val startProcessor = StartProcessor(sBatchGenerator, sshConnectionPool, sshConfig.user)

    private fun validateAndConnectToStorage(requestHeader: RequestHeader): Result<StorageConnection> =
            with(requestHeader.performedFor) { storageConnectionFactory.createForAccount(username, password) }

    fun constructStreams(builder: KStreamBuilder) {
        if (false) {
            // TODO FIX THIS!!
            // Serialization errors will always cause a crash. We need those messages to be ignored instead.
            builder.stream<String, String>(Serdes.String(), Serdes.String(),
                    TOPIC_HPC_APP_REQUESTS).print()

            builder.stream<String, String>(Serdes.String(), Serdes.String(),
                    TOPIC_HPC_APP_EVENTS).print()

            builder.stream<String, String>(Serdes.String(), Serdes.String(), "storage-processor-jobToApp-changelog")
                    .print()
            return
        }

        val requests = builder.stream<String, Request<HPCAppRequest>>(Serdes.String(), jsonSerde(),
                TOPIC_HPC_APP_REQUESTS)

        val events = requests
                .mapValues { request ->
                    log.info("Incoming request: $request")
                    val storage: Result<StorageConnection> = validateAndConnectToStorage(request.header)

                    // TODO We still need a clear plan for how to deal with this during replays.
                    @Suppress("UNCHECKED_CAST")
                    when (request.event) {
                        is HPCAppRequest.Start -> startProcessor.handle(
                                storage,
                                request as Request<HPCAppRequest.Start>
                        )
                        is HPCAppRequest.Cancel -> HPCAppEvent.UnsuccessfullyCompleted(Error.invalidMessage())
                    }
                }
                .through(Serdes.String(), jsonSerde(), TOPIC_HPC_APP_EVENTS)

        // Keep a mapping between slurm ids and job ids
        val pendingEvents = events.filterIsInstance(HPCAppEvent.Pending::class)

        pendingEvents
                .map { systemId, event -> KeyValue(event.jobId, systemId) }
                .groupByKey(Serdes.Long(), Serdes.String())
                .aggregate(
                        { null }, // aggregate initializer
                        { _, newValue, _ -> newValue }, // aggregator
                        Serdes.String(), // value serde
                        TOPIC_SLURM_TO_JOB_ID // table name
                )

        // Keep a mapping between internal job ids and their pending event
        val serde = jsonSerde<HPCAppEvent.Pending>()
        pendingEvents
                .groupByKey(Serdes.String(), serde)
                .aggregate(
                        { null },
                        { _, newValue, _ -> newValue },
                        serde,
                        TOPIC_JOB_ID_TO_APP
                )

        // TODO This needs to pick up stuff that it lost by replaying (actually, all of the iqueries need to do so)
        events.groupByKey(Serdes.String(), jsonSerde()).aggregate(
                { null },
                { _, newValue, _ ->
                    slurmPollAgent.handle(newValue)
                    newValue
                },
                jsonSerde<HPCAppEvent>(),
                "appStatus"
        )
    }

    private fun <K, V : Any, R : V> KStream<K, V>.filterIsInstance(klass: KClass<R>) =
            filter { _, value -> klass.isInstance(value) }.mapValues {
                @Suppress("UNCHECKED_CAST")
                it as R
            }
}