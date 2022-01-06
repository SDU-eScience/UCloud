package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductType
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.serializer

typealias DeviceSvcSuper = ResourceService<SyncDevice, SyncDevice.Spec, SyncDevice.Update, SyncDeviceIncludeFlags,
    SyncDevice.Status, Product.Synchronization, SyncDeviceSupport, SimpleProviderCommunication>

class SyncDeviceService(
    db: AsyncDBSessionFactory,
    providers: Providers<SimpleProviderCommunication>,
    support: ProviderSupport<SimpleProviderCommunication, Product.Synchronization, SyncDeviceSupport>,
    serviceClient: AuthenticatedClient,
) : DeviceSvcSuper(db, providers, support, serviceClient) {
    override val table = SqlObject.Table("file_orchestrator.sync_devices")
    override val defaultSortColumn = SqlObject.Column(table, "resource")
    override val sortColumns: Map<String, SqlObject.Column> = mapOf("resource" to defaultSortColumn)
    override val serializer = serializer<SyncDevice>()
    override val updateSerializer = serializer<SyncDevice.Update>()
    override val productArea = ProductType.SYNCHRONIZATION

    override fun userApi() = SyncDevices
    override fun controlApi() = SyncDeviceControl
    override fun providerApi(comms: ProviderComms) = SyncDeviceProvider(comms.provider.id)

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, SyncDevice.Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {

        session
            .sendPreparedStatement(
                {
                    val ids by parameterList<Long>()
                    val deviceIds by parameterList<String>()
                    for ((id, spec) in idWithSpec) {
                        ids.add(id)
                        deviceIds.add(spec.deviceId)
                    }
                },
                """
                    insert into file_orchestrator.sync_devices (resource, device_id)
                    select unnest(:ids::bigint[]), unnest(:device_ids::text[])
                    on conflict (resource) do nothing
                """
            )
    }

    override suspend fun browseQuery(
        actorAndProject: ActorAndProject,
        flags: SyncDeviceIncludeFlags?,
        query: String?
    ): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
                setParameter("filter_owner", flags?.filterOwner)
            },
            """
                select *
                from file_orchestrator.sync_devices
                where
                    (:query::text is null or device_id ilike ('%' || :query || '%')) and
                    (:filter_owner::text[] is null
                        or array_length(:filter_owner::text[], 1) < 1
                        or resource in (
                            select id from provider.resource where type = 'sync_device' and created_by in (select unnest(:filter_owner::text[]))
                        )
                    )
            """
        )
    }
}