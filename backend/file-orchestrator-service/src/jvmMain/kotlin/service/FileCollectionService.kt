package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.accounting.api.providers.ResourceProviderApi
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.FEATURE_NOT_SUPPORTED_BY_PROVIDER
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceUpdate
import dk.sdu.cloud.provider.api.UpdatedAcl
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

private typealias Super = ResourceService<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport, StorageCommunication>

class FileCollectionService(
    db: AsyncDBSessionFactory,
    providers: Providers<StorageCommunication>,
    support: ProviderSupport<StorageCommunication, Product.Storage, FSSupport>,
    serviceClient: AuthenticatedClient,
) : Super(db, providers, support, serviceClient) {
    override val table = SqlObject.Table("file_orchestrator.file_collections")
    override val defaultSortColumn = SqlObject.Column(table, "resource")
    override val sortColumns = mapOf(
        "resource" to SqlObject.Column(table, "resource")
    )

    override val serializer = serializer<FileCollection>()
    override val updateSerializer = serializer<FileCollection.Update>()
    override val productArea = ProductArea.STORAGE

    override fun userApi() = FileCollections
    override fun controlApi() = FileCollectionsControl
    override fun providerApi(comms: ProviderComms) = FileCollectionsProvider(comms.provider.id)

    override suspend fun createSpecifications(
        actorAndProject: ActorAndProject,
        idWithSpec: List<Pair<Long, FileCollection.Spec>>,
        session: AsyncDBConnection,
        allowDuplicates: Boolean
    ) {
        session.sendPreparedStatement(
            {
                val titles by parameterList<String>()
                val ids by parameterList<Long>()
                for ((id, spec) in idWithSpec) {
                    ids.add(id)
                    titles.add(spec.title)
                }
            },
            """
                insert into file_orchestrator.file_collections (resource, title)
                select unnest(:ids::bigint[]) id, unnest(:titles::text[]) title
            """
        )
    }

    override suspend fun browseQuery(flags: FileCollectionIncludeFlags?, query: String?): PartialQuery {
        return PartialQuery(
            {
                setParameter("query", query)
            },
            """
                select * from file_orchestrator.file_collections
                where
                    (:query::text is null or title ilike '%' || :query || '%')
            """
        )
    }

    override suspend fun verifyProviderSupportsCreate(
        spec: FileCollection.Spec,
        res: ProductRefOrResource<FileCollection>,
        support: FSSupport
    ) {
        if (support.collection.usersCanCreate == false) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest, FEATURE_NOT_SUPPORTED_BY_PROVIDER)
        }
    }

    override fun verifyProviderSupportsDelete(
        id: FindByStringId,
        res: ProductRefOrResource<FileCollection>,
        support: FSSupport
    ) {
        if (support.collection.usersCanDelete == false) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest, FEATURE_NOT_SUPPORTED_BY_PROVIDER)
        }
    }

    override fun verifyProviderSupportsUpdateAcl(
        spec: UpdatedAcl,
        res: ProductRefOrResource<FileCollection>,
        support: FSSupport
    ) {
        if (support.collection.aclModifiable == false) {
            throw RPCException("Not supported", HttpStatusCode.BadRequest, FEATURE_NOT_SUPPORTED_BY_PROVIDER)
        }
    }
}
