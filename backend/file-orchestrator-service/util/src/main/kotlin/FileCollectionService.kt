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

typealias AclUpdateHandler = suspend (session: AsyncDBConnection, batch: BulkRequest<UpdatedAclWithResource<FileCollection>>) -> Unit
typealias DeleteHandler = suspend (batch: BulkRequest<FindByStringId>) -> Unit

private typealias Super = ResourceService<FileCollection, FileCollection.Spec, FileCollection.Update,
    FileCollectionIncludeFlags, FileCollection.Status, Product.Storage, FSSupport, StorageCommunication>

class FileCollectionService(
    projectCache: ProjectCache,
    db: AsyncDBSessionFactory,
    providers: Providers<StorageCommunication>,
    support: ProviderSupport<StorageCommunication, Product.Storage, FSSupport>,
    serviceClient: AuthenticatedClient,
) : Super(projectCache, db, providers, support, serviceClient) {
    private val aclUpdateHandlers = ArrayList<AclUpdateHandler>()
    override val table = SqlObject.Table("file_orchestrator.file_collections")
    override val defaultSortColumn = SqlObject.Column(table, "title")
    override val sortColumns = mapOf(
        "title" to SqlObject.Column(table, "title")
    )

    private val deleteHandlers = ArrayList<DeleteHandler>()
    override val serializer = FileCollection.serializer()
    override val updateSerializer = FileCollection.Update.serializer()
    override val productArea = ProductArea.STORAGE
    override val requireAdminForCreate: Boolean = true

    override fun userApi() = FileCollections
    override fun controlApi() = FileCollectionsControl
    override fun providerApi(comms: ProviderComms) = FileCollectionsProvider(comms.provider.id)

    fun addAclUpdateHandler(handler: AclUpdateHandler) {
        aclUpdateHandlers.add(handler)
    }

    fun addDeleteHandler(handler: DeleteHandler) {
        deleteHandlers.add(handler)
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
                on conflict do nothing
            """
        )
    }

    override suspend fun browseQuery(
        actorAndProject: ActorAndProject,
        flags: FileCollectionIncludeFlags?,
        query: String?
    ): PartialQuery {
        return PartialQuery(
            {
                setParameter("username", actorAndProject.actor.safeUsername())
                setParameter("query", query)
                setParameter(
                    "filter_member_files",
                    flags?.filterMemberFiles?.name ?: MemberFilesFilter.DONT_FILTER_COLLECTIONS.name
                )
            },
            """
                select c.*
                from
                    accessible_resources resc join
                    file_orchestrator.file_collections c on (resc.r).id = resource
                where
                    (:query::text is null or title ilike '%' || :query || '%') and
                    (
                        :filter_member_files = '${MemberFilesFilter.DONT_FILTER_COLLECTIONS.name}' or
                        (
                            :filter_member_files = '${MemberFilesFilter.SHOW_ONLY_MINE.name}' and
                            (
                                c.title = 'Member Files: ' || :username or
                                c.title not like 'Member Files: %'
                            )
                        ) or
                        (
                            :filter_member_files = '${MemberFilesFilter.SHOW_ONLY_MEMBER_FILES.name}' and
                            c.title like 'Member Files: %'
                        )
                    )
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

    override suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        deleteHandlers.forEach { handler -> handler(request) }
        return super.delete(actorAndProject, request)
    }
}
