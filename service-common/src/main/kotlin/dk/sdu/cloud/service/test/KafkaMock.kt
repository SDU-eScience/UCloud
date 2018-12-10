package dk.sdu.cloud.service.test

import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.StreamDescription
import io.mockk.mockk
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AlterConfigsOptions
import org.apache.kafka.clients.admin.AlterConfigsResult
import org.apache.kafka.clients.admin.AlterReplicaLogDirsOptions
import org.apache.kafka.clients.admin.AlterReplicaLogDirsResult
import org.apache.kafka.clients.admin.Config
import org.apache.kafka.clients.admin.CreateAclsOptions
import org.apache.kafka.clients.admin.CreateAclsResult
import org.apache.kafka.clients.admin.CreateDelegationTokenOptions
import org.apache.kafka.clients.admin.CreateDelegationTokenResult
import org.apache.kafka.clients.admin.CreatePartitionsOptions
import org.apache.kafka.clients.admin.CreatePartitionsResult
import org.apache.kafka.clients.admin.CreateTopicsOptions
import org.apache.kafka.clients.admin.CreateTopicsResult
import org.apache.kafka.clients.admin.DeleteAclsOptions
import org.apache.kafka.clients.admin.DeleteAclsResult
import org.apache.kafka.clients.admin.DeleteConsumerGroupsOptions
import org.apache.kafka.clients.admin.DeleteConsumerGroupsResult
import org.apache.kafka.clients.admin.DeleteRecordsOptions
import org.apache.kafka.clients.admin.DeleteRecordsResult
import org.apache.kafka.clients.admin.DeleteTopicsOptions
import org.apache.kafka.clients.admin.DeleteTopicsResult
import org.apache.kafka.clients.admin.DescribeAclsOptions
import org.apache.kafka.clients.admin.DescribeAclsResult
import org.apache.kafka.clients.admin.DescribeClusterOptions
import org.apache.kafka.clients.admin.DescribeClusterResult
import org.apache.kafka.clients.admin.DescribeConfigsOptions
import org.apache.kafka.clients.admin.DescribeConfigsResult
import org.apache.kafka.clients.admin.DescribeConsumerGroupsOptions
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult
import org.apache.kafka.clients.admin.DescribeDelegationTokenOptions
import org.apache.kafka.clients.admin.DescribeDelegationTokenResult
import org.apache.kafka.clients.admin.DescribeLogDirsOptions
import org.apache.kafka.clients.admin.DescribeLogDirsResult
import org.apache.kafka.clients.admin.DescribeReplicaLogDirsOptions
import org.apache.kafka.clients.admin.DescribeReplicaLogDirsResult
import org.apache.kafka.clients.admin.DescribeTopicsOptions
import org.apache.kafka.clients.admin.DescribeTopicsResult
import org.apache.kafka.clients.admin.ExpireDelegationTokenOptions
import org.apache.kafka.clients.admin.ExpireDelegationTokenResult
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsOptions
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult
import org.apache.kafka.clients.admin.ListConsumerGroupsOptions
import org.apache.kafka.clients.admin.ListConsumerGroupsResult
import org.apache.kafka.clients.admin.ListTopicsOptions
import org.apache.kafka.clients.admin.ListTopicsResult
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.admin.RecordsToDelete
import org.apache.kafka.clients.admin.RenewDelegationTokenOptions
import org.apache.kafka.clients.admin.RenewDelegationTokenResult
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.Metric
import org.apache.kafka.common.MetricName
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.TopicPartitionReplica
import org.apache.kafka.common.acl.AclBinding
import org.apache.kafka.common.acl.AclBindingFilter
import org.apache.kafka.common.config.ConfigResource
import java.util.*
import java.util.concurrent.TimeUnit

object KafkaMock : Loggable {
    override val log = logger()

    lateinit var mockedKafkaProducer: MockProducer<String, String>
        private set

    val mockedAdminClient: AdminClient = MockedAdminClient()

    val recordedProducerRecords: List<ProducerRecord<String, String>>
        get() {
            return mockedKafkaProducer.history()
        }

    fun initialize(): KafkaServices {
        log.debug("initialize()")
        reset()

        // Create mocks
        log.debug("  Creating kafkaProducer")
        val kafkaProducer = MockProducer<String, String>(Cluster.empty(), true, null, null, null)
        this.mockedKafkaProducer = kafkaProducer

        log.debug("  Returning from initialize()")
        return KafkaServices(
            streamsConfig = Properties(),
            consumerConfig = Properties(),
            producer = kafkaProducer,
            adminClient = mockedAdminClient
        )
    }


    fun reset() {
        if (this::mockedKafkaProducer.isInitialized) {
            mockedKafkaProducer.clear()
        }
    }
}

/**
 * Mocked Kafka is expensive and we guarantee an [AdminClient] to be present.
 *
 * This class will skip the initial mock (because we rarely need an admin client in tests) and then mock all calls.
 */
class MockedAdminClient : AdminClient() {
    override fun metrics(): MutableMap<MetricName, out Metric> {
        return mockk(relaxed = true)
    }

    override fun describeTopics(
        topicNames: MutableCollection<String>?,
        options: DescribeTopicsOptions?
    ): DescribeTopicsResult {
        return mockk(relaxed = true)
    }

    override fun expireDelegationToken(
        hmac: ByteArray?,
        options: ExpireDelegationTokenOptions?
    ): ExpireDelegationTokenResult {
        return mockk(relaxed = true)
    }

    override fun listConsumerGroups(options: ListConsumerGroupsOptions?): ListConsumerGroupsResult {
        return mockk(relaxed = true)
    }

    override fun describeCluster(options: DescribeClusterOptions?): DescribeClusterResult {
        return mockk(relaxed = true)
    }

    override fun deleteTopics(topics: MutableCollection<String>?, options: DeleteTopicsOptions?): DeleteTopicsResult {
        return mockk(relaxed = true)
    }

    override fun alterConfigs(
        configs: MutableMap<ConfigResource, Config>?,
        options: AlterConfigsOptions?
    ): AlterConfigsResult {
        return mockk(relaxed = true)
    }

    override fun createDelegationToken(options: CreateDelegationTokenOptions?): CreateDelegationTokenResult {
        return mockk(relaxed = true)
    }

    override fun describeAcls(filter: AclBindingFilter?, options: DescribeAclsOptions?): DescribeAclsResult {
        return mockk(relaxed = true)
    }

    override fun listConsumerGroupOffsets(
        groupId: String?,
        options: ListConsumerGroupOffsetsOptions?
    ): ListConsumerGroupOffsetsResult {
        return mockk(relaxed = true)
    }

    override fun close(duration: Long, unit: TimeUnit?) {
        return mockk(relaxed = true)
    }

    override fun describeDelegationToken(options: DescribeDelegationTokenOptions?): DescribeDelegationTokenResult {
        return mockk(relaxed = true)
    }

    override fun describeConsumerGroups(
        groupIds: MutableCollection<String>?,
        options: DescribeConsumerGroupsOptions?
    ): DescribeConsumerGroupsResult {
        return mockk(relaxed = true)
    }

    override fun describeReplicaLogDirs(
        replicas: MutableCollection<TopicPartitionReplica>?,
        options: DescribeReplicaLogDirsOptions?
    ): DescribeReplicaLogDirsResult {
        return mockk(relaxed = true)
    }

    override fun createPartitions(
        newPartitions: MutableMap<String, NewPartitions>?,
        options: CreatePartitionsOptions?
    ): CreatePartitionsResult {
        return mockk(relaxed = true)
    }

    override fun alterReplicaLogDirs(
        replicaAssignment: MutableMap<TopicPartitionReplica, String>?,
        options: AlterReplicaLogDirsOptions?
    ): AlterReplicaLogDirsResult {
        return mockk(relaxed = true)
    }

    override fun deleteConsumerGroups(
        groupIds: MutableCollection<String>?,
        options: DeleteConsumerGroupsOptions?
    ): DeleteConsumerGroupsResult {
        return mockk(relaxed = true)
    }

    override fun listTopics(options: ListTopicsOptions?): ListTopicsResult {
        return mockk(relaxed = true)
    }

    override fun renewDelegationToken(
        hmac: ByteArray?,
        options: RenewDelegationTokenOptions?
    ): RenewDelegationTokenResult {
        return mockk(relaxed = true)
    }

    override fun deleteRecords(
        recordsToDelete: MutableMap<TopicPartition, RecordsToDelete>?,
        options: DeleteRecordsOptions?
    ): DeleteRecordsResult {
        return mockk(relaxed = true)
    }

    override fun createAcls(acls: MutableCollection<AclBinding>?, options: CreateAclsOptions?): CreateAclsResult {
        return mockk(relaxed = true)
    }

    override fun describeConfigs(
        resources: MutableCollection<ConfigResource>?,
        options: DescribeConfigsOptions?
    ): DescribeConfigsResult {
        return mockk(relaxed = true)
    }

    override fun createTopics(
        newTopics: MutableCollection<NewTopic>?,
        options: CreateTopicsOptions?
    ): CreateTopicsResult {
        return mockk(relaxed = true)
    }

    override fun deleteAcls(
        filters: MutableCollection<AclBindingFilter>?,
        options: DeleteAclsOptions?
    ): DeleteAclsResult {
        return mockk(relaxed = true)
    }

    override fun describeLogDirs(
        brokers: MutableCollection<Int>?,
        options: DescribeLogDirsOptions?
    ): DescribeLogDirsResult {
        return mockk(relaxed = true)
    }
}


fun <Key, Value> KafkaMock.assertKVMessage(topic: StreamDescription<Key, Value>, key: Key, value: Value) {
    assert(recordedProducerRecords.any {
        it.topic() == topic.name &&
                it.key() == topic.keySerde.serializer().serialize(topic.name, key).toString(Charsets.UTF_8) &&
                it.value() == topic.valueSerde.serializer().serialize(topic.name, value).toString(Charsets.UTF_8)
    })
}

fun <Value> KafkaMock.assertMessage(topic: StreamDescription<*, Value>, value: Value) {
    assert(recordedProducerRecords.any {
        it.topic() == topic.name &&
                it.value() == topic.valueSerde.serializer().serialize(topic.name, value).toString(Charsets.UTF_8)
    })
}

fun <Value> KafkaMock.assertMessageThat(topic: StreamDescription<*, Value>, matcher: (Value) -> Boolean) {
    assertCollectionHasItem(messagesForTopic(topic).map { it.second }, matcher = matcher)
}

fun <Key, Value> KafkaMock.messagesForTopic(topic: StreamDescription<Key, Value>): List<Pair<Key, Value>> {
    return recordedProducerRecords.filter { it.topic() == topic.name }.map {
        Pair(
            topic.keySerde.deserializer().deserialize(topic.name, it.key().toByteArray(Charsets.UTF_8)),
            topic.valueSerde.deserializer().deserialize(topic.name, it.value().toByteArray(Charsets.UTF_8))
        )
    }
}
