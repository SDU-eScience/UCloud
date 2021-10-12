package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductArea
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.accounting.util.ProviderSupport
import dk.sdu.cloud.accounting.util.Providers
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.serializer


typealias AclUpdateHandler = suspend (session: AsyncDBConnection, batch: BulkRequest<UpdatedAclWithResource<FileCollection>>) -> Unit

private typealias Super = ResourceService<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport, StorageCommunication>

class FileCollectionService(
    db: AsyncDBSessionFactory,
    providers: Providers<StorageCommunication>,
    support: ProviderSupport<StorageCommunication, Product.Storage, FSSupport>,
    serviceClient: AuthenticatedClient,
) : Super(db, providers, support, serviceClient) {
    private val aclUpdateHandlers = ArrayList<AclUpdateHandler>()
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

    fun addAclUpdateHandler(handler: AclUpdateHandler) {
        aclUpdateHandlers.add(handler)
    }

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

    suspend fun rename(
        actorAndProject: ActorAndProject,
        request: FileCollectionsRenameRequest
    ) {
        proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection,
                FileCollectionsRenameRequestItem, FileCollectionsProviderRenameRequest,
                Unit>() {
                override val isUserRequest: Boolean = true
                override fun retrieveCall(comms: StorageCommunication) = comms.fileCollectionsApi.rename

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<FileCollectionsRenameRequestItem>
                ): List<RequestWithRefOrResource<FileCollectionsRenameRequestItem, FileCollection>> {
                    val ids = request.items.map { it.id }.toSet()
                    val collections = retrieveBulk(
                        actorAndProject,
                        ids,
                        listOf(Permission.EDIT),
                        simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
                    ).associateBy { it.id }
                    return request.items.map { it to ProductRefOrResource.SomeResource(collections.getValue(it.id)) }
                }

                override suspend fun verifyRequest(
                    request: FileCollectionsRenameRequestItem,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (support.collection.usersCanRename != true) {
                        throw RPCException(
                            "Your provider does not allow you to rename this drive",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FileCollectionsRenameRequestItem, FileCollection>>
                ): FileCollectionsProviderRenameRequest {
                    return BulkRequest(resources.map { (request) ->
                        FileCollectionsProviderRenameRequestItem(request.id, request.newTitle)
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FileCollectionsRenameRequestItem, FileCollection>>,
                    response: BulkResponse<Unit?>
                ) {
                    db.withSession { session ->
                        session.sendPreparedStatement(
                            {
                                resources.split {
                                    into("ids") { it.first.id.toLong() }
                                    into("new_titles") { it.first.newTitle }
                                }
                            },
                            """
                                with requests as (
                                    select unnest(:ids::bigint[]) id, unnest(:new_titles::text[]) new_title
                                )
                                update file_orchestrator.file_collections coll
                                set title = req.new_title
                                from requests req
                                where coll.resource = req.id
                            """
                        )
                    }
                }
            }
        )
    }

    override suspend fun onUpdateAcl(
        session: AsyncDBConnection,
        request: BulkRequest<UpdatedAclWithResource<FileCollection>>
    ) {
        aclUpdateHandlers.forEach { it(session, request) }
    }
}
