package dk.sdu.cloud.file.orchestrator.service

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.accounting.api.providers.*
import dk.sdu.cloud.accounting.util.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.sendWSMessage
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.file.orchestrator.api.extractPathMetadata
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.actorAndProject
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.task.api.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.IllegalArgumentException

typealias MoveHandler = suspend (batch: List<FilesMoveRequestItem>) -> Unit
typealias DeleteFileHandler = suspend (batch: List<FindByStringId>) -> Unit
typealias TrashHandler = suspend (batch: List<FindByPath>) -> Unit

class FilesService(
    private val fileCollections: FileCollectionService,
    private val providers: StorageProviders,
    private val providerSupport: StorageProviderSupport,
    private val metadataService: MetadataService,
    private val templates: MetadataTemplateNamespaces,
    private val serviceClient: AuthenticatedClient,
    private val db: DBContext,
) : ResourceSvc<UFile, UFileIncludeFlags, UFileSpecification, UFileUpdate, Product.Storage, FSSupport> {
    private val moveHandlers = ArrayList<MoveHandler>()
    private val deleteHandlers = ArrayList<DeleteFileHandler>()
    private val trashHandlers = ArrayList<TrashHandler>()
    private val proxy =
        ProviderProxy<StorageCommunication, Product.Storage, FSSupport, UFile>(providers, providerSupport)
    private val metadataCache = SimpleCache<Pair<ActorAndProject, String>, MetadataService.RetrieveWithHistory>(
        lookup = { (actorAndProject, parentPath) ->
            metadataService.retrieveWithHistory(actorAndProject, parentPath)
        }
    )

    private var cachedSensitivityTemplate: FileMetadataTemplate? = null
    private val sensitivityTemplateMutex = Mutex()
    private var ucloudShareReference: ProductReference? = null

    init {
        // TODO(Dan): This is extremely fragile as it will only work for one specific provider
        ucloudShareReference = runBlocking {
            db.withSession { session ->
                session
                    .sendPreparedStatement(
                        """
                        select r.type, pc.category, pc.provider
                        from provider.resource r
                        join accounting.products p on p.id = r.product
                        join accounting.product_categories pc on pc.id = p.category
                        where r.type = 'share' and
                            r.provider = 'ucloud'
                    """.trimIndent()
                    ).rows
                    .singleOrNull()?.let {
                        ProductReference(
                            it.getString(0)!!,
                            it.getString(1)!!,
                            it.getString(2)!!
                        )
                    }

            }
        }
    }

    private suspend fun retrieveSensitivityTemplate(): FileMetadataTemplate {
        if (cachedSensitivityTemplate != null) {
            return cachedSensitivityTemplate!!
        }

        sensitivityTemplateMutex.withLock {
            if (cachedSensitivityTemplate != null) {
                return cachedSensitivityTemplate!!
            }

            val errorMessage = "Sensitivity template is not registered in UCloud's database. " +
                    "Is database corrupt?"

            val namespace = templates.browse(
                ActorAndProject(Actor.System, null),
                ResourceBrowseRequest(FileMetadataTemplateNamespaceFlags(filterName = "sensitivity"))
            ).items.singleOrNull() ?: error(errorMessage)

            val template = templates.browseTemplates(
                ActorAndProject(Actor.System, null),
                FileMetadataTemplatesBrowseTemplatesRequest(namespace.id)
            ).items.singleOrNull() ?: error(errorMessage)

            cachedSensitivityTemplate = template
            return template
        }
    }

    fun addMoveHandler(handler: MoveHandler) {
        moveHandlers.add(handler)
    }

    fun addDeleteFileHandler(handler: DeleteFileHandler) {
        deleteHandlers.add(handler)
    }

    fun addTrashHandler(handler: TrashHandler) {
        trashHandlers.add(handler)
    }

    private fun verifyReadRequest(request: UFileIncludeFlags, support: FSSupport) {
        if (request.allowUnsupportedInclude != true) {
            // Request verification is needed
            if (request.includePermissions == true) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }

            if (request.includeSizes == true && support.stats.sizeInBytes != true &&
                support.stats.sizeIncludingChildrenInBytes != true
            ) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }

            if (request.includeTimestamps == true && support.stats.accessedAt != true &&
                support.stats.createdAt != true && support.stats.modifiedAt != true
            ) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }

            if (request.includeUnixInfo == true && support.stats.unixOwner != true &&
                support.stats.unixGroup != true && support.stats.unixPermissions != true
            ) {
                throw RPCException("Operation not supported by the provider", HttpStatusCode.BadRequest)
            }
        }
    }

    private suspend fun providerFromPath(
        actorAndProject: ActorAndProject,
        path: String?,
        permission: Permission
    ): String {
        if (path == null) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val collection = extractPathMetadata(path).collection
        return fileCollections.retrieveBulk(
            actorAndProject,
            listOf(collection),
            listOf(permission),
            simpleFlags = SimpleResourceIncludeFlags(includeSupport = false)
        ).singleOrNull()?.specification?.product?.provider ?: throw RPCException(
            "File not found",
            HttpStatusCode.NotFound
        )
    }


    private suspend fun collectionFromPath(
        actorAndProject: ActorAndProject,
        path: String?,
        permission: Permission
    ): FileCollection {
        if (path == null) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val collection = extractPathMetadata(path).collection
        return fileCollections.retrieveBulk(
            actorAndProject,
            listOf(collection),
            listOf(permission),
            simpleFlags = SimpleResourceIncludeFlags(includeSupport = true)
        ).singleOrNull() ?: throw RPCException("File not found", HttpStatusCode.NotFound)
    }

    override suspend fun browse(
        actorAndProject: ActorAndProject,
        request: ResourceBrowseRequest<UFileIncludeFlags>,
        useProject: Boolean,
        ctx: DBContext?
    ): PageV2<UFile> {
        val path = request.flags.path ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val resolvedCollection = collectionFromPath(actorAndProject, path, Permission.READ)
        verifyReadRequest(request.flags, resolvedCollection.status.resolvedSupport!!.support)
        return coroutineScope {
            val browseJob = async {
                proxy.pureProxy(
                    actorAndProject,
                    resolvedCollection.specification.product,
                    { it.filesApi.browse },
                    FilesProviderBrowseRequest(resolvedCollection, request)
                )
            }

            val metadataJob = async {
                if (request.flags.includeMetadata == true) {
                    metadataCache.get(Pair(actorAndProject, path))
                } else {
                    null
                }
            }

            val browse = browseJob.await()
            val metadata = metadataJob.await()

            browse.mapItems {
                it.toUFile(resolvedCollection, metadata)
            }
        }
    }

    /*
     * ReplaceString should begin with / and end with /
     */
    suspend fun replaceFileCollectionIdWhenShare(path: String): String {
        if (path.startsWith("/")) {
            val id = "/" + path.components().first()
            val replaceString = db.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("id", id)
                        },
                        """
                            select original_file_path
                            from file_orchestrator.shares
                            where available_at = :id
                        """
                    ).rows
                    .singleOrNull()
                    ?.getString(0)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "share path missing")
            }
            if (replaceString.startsWith("/")) {
                return path.replace(Regex("""^\/\d+"""), replaceString)
            } else {
                throw IllegalArgumentException("Should start with /")
            }
        } else {
            throw IllegalArgumentException("Path should start with /")
        }
    }

    private suspend fun PartialUFile.toUFile(
        resolvedCollection: FileCollection,
        metadata: MetadataService.RetrieveWithHistory?,
    ): UFile {
        val metadataHistory = if (metadata != null) {
            val resolvedIdIfShare =
                if (ucloudShareReference != null && resolvedCollection.specification.product == ucloudShareReference) {
                    replaceFileCollectionIdWhenShare(id)
                } else {
                    id
                }
            val inheritedMetadata = resolvedIdIfShare.parents().asReversed().mapNotNull { parent ->
                metadata.metadataByFile[parent.removeSuffix("/")]?.mapNotNull { (template, docs) ->
                    // Pick only the latest version and only if it is not a deletion
                    // NOTE(Dan): If any has been approved, it will always be placed as the first element. This is also
                    // why we need to check to see if any has been approved.
                    val validDoc = docs[0]
                    if (validDoc is FileMetadataDocument &&
                        (validDoc.status.approval == FileMetadataDocument.ApprovalStatus.NotRequired ||
                                validDoc.status.approval is FileMetadataDocument.ApprovalStatus.Approved)
                    ) {
                        template to validDoc
                    } else {
                        null
                    }
                }?.toMap()
            }
            val templates = metadata.templates.toMutableMap()
            val history = HashMap<String, List<FileMetadataOrDeleted>>()
            // NOTE(Dan): First we pre-fill the history with the inherited metadata. This metadata is sorted such that
            // the highest priority is listed first, which means we shouldn't override if an existing entry is present.
            for (inherited in inheritedMetadata) {
                inherited.forEach { (template, document) ->
                    if (template !in history && metadata.templates.getValue(template).inheritable) {
                        history[template] = listOf(document)
                    }
                }
            }
            // NOTE(Dan): And then we insert the local metadata, overriding any existing entry.

            val ownMetadata = metadata.metadataByFile[resolvedIdIfShare] ?: emptyMap()
            ownMetadata.forEach { (template, docs) ->
                history[template] = docs
            }

            if (legacySensitivity != null) {
                val template = retrieveSensitivityTemplate()
                templates[template.namespaceId] = template
                if (template.namespaceId !in history) {
                    history[template.namespaceId] = listOf(
                        FileMetadataDocument(
                            "LEGACY-SENSITIVITY",
                            FileMetadataDocument.Spec(
                                template.namespaceId,
                                template.version,
                                JsonObject(
                                    mapOf(
                                        "sensitivity" to JsonPrimitive(legacySensitivity)
                                    )
                                ),
                                ""
                            ),
                            Time.now(),
                            FileMetadataDocument.Status(FileMetadataDocument.ApprovalStatus.NotRequired),
                            "_ucloud"
                        )
                    )
                }
            }

            FileMetadataHistory(templates, history)
        } else {
            null
        }

        return UFile(
            id,
            UFileSpecification(
                resolvedCollection.id,
                resolvedCollection.specification.product
            ),
            createdAt,
            status.copy(metadata = metadataHistory),
            owner ?: resolvedCollection.owner,
            permissions ?: resolvedCollection.permissions,
        )
    }

    override suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        flags: UFileIncludeFlags?,
        ctx: DBContext?,
        asProvider: Boolean
    ): UFile {
        val path = flags?.path ?: id
        val resolvedCollection = collectionFromPath(actorAndProject, path, Permission.READ)
        verifyReadRequest(flags ?: UFileIncludeFlags(), resolvedCollection.status.resolvedSupport!!.support)

        return coroutineScope {
            val retrieveJob = async {
                proxy.pureProxy(
                    actorAndProject,
                    resolvedCollection.specification.product,
                    { it.filesApi.retrieve },
                    FilesProviderRetrieveRequest(
                        resolvedCollection,
                        ResourceRetrieveRequest(flags ?: UFileIncludeFlags(), id)
                    )
                )
            }

            val metadataJob = async {
                if (flags?.includeMetadata == true) {
                    metadataService.retrieveWithHistory(
                        actorAndProject,
                        path.parent(),
                        listOf(path.fileName())
                    )
                } else {
                    null
                }
            }

            retrieveJob.await().toUFile(resolvedCollection, metadataJob.await())
        }
    }

    override suspend fun updateAcl(
        actorAndProject: ActorAndProject,
        request: BulkRequest<UpdatedAcl>
    ): BulkResponse<Unit?> {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.ADMIN,
            { it.filesApi.updateAcl },
            { extractPathMetadata(it.id).collection },
            { req, coll -> UpdatedAclWithResource(dummyResource(req.id, coll), req.added, req.deleted) },
            dontTolerateReadOnly = false,
            checkRequest = { _, fsSupport ->
                if (!fsSupport.files.aclModifiable) {
                    throw RPCException(
                        "You cannot change the ACL of this file. Try a different drive.",
                        HttpStatusCode.BadRequest,
                        FEATURE_NOT_SUPPORTED_BY_PROVIDER
                    )
                }
            }
        )
    }

    override suspend fun delete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>
    ): BulkResponse<Unit?> {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.EDIT,
            { it.filesApi.delete },
            { extractPathMetadata(it.id).collection },
            { req, coll -> dummyResource(req.id, coll) },
            afterCall = { _, resources, response ->
                val batch = ArrayList<FindByStringId>()
                for ((index, res) in resources.withIndex()) {
                    if (response.responses[index] != null) {
                        batch.add(res.first)
                    }
                }

                deleteHandlers.forEach { handler -> handler(batch) }
            }
        )
    }

    override suspend fun retrieveProducts(
        actorAndProject: ActorAndProject
    ): SupportByProvider<Product.Storage, FSSupport> {
        return fileCollections.retrieveProducts(actorAndProject)
    }

    override suspend fun search(
        actorAndProject: ActorAndProject,
        request: ResourceSearchRequest<UFileIncludeFlags>,
        ctx: DBContext?
    ): PageV2<UFile> {
        return (ctx ?: db).withSession { session ->
            val owner = ResourceOwner(actorAndProject.actor.safeUsername(), actorAndProject.project)

            val isMemberOfProject = if (actorAndProject.project == null) {
                true
            } else {
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        select true
                        from project.project_members pm
                        where pm.username = :username and pm.project_id = :project
                    """
                ).rows.isNotEmpty()
            }

            if (!isMemberOfProject) {
                throw RPCException("Bad project supplied", HttpStatusCode.Forbidden)
            }

            val relevantProviders = retrieveRelevantProviders(actorAndProject)

            val collectionIds = HashSet<String>()
            val partialsByParent = HashMap<String, List<PartialUFile>>()

            val nextByProvider: Map<String, String> = (request.next ?: "")
                .split(HACKY_SEARCH_SEPARATOR)
                .asSequence()
                .windowed(2)
                .filter { it.size == 2 }
                .map { it[0] to it[1] }
                .toMap()

            val newNextByProvider = HashMap<String, String>()

            // TODO(Dan): We should probably not combine data in this way
            // TODO(Dan): We need a better way of detecting support for search
            val allResults = relevantProviders.flatMap { provider ->
                runCatching {
                    val resp = proxy.invokeCall(
                        actorAndProject,
                        true,
                        FilesProviderSearchRequest(
                            request.query,
                            owner,
                            request.flags,
                            null,
                            request.itemsPerPage,
                            nextByProvider[provider],
                            request.consistency,
                            request.itemsToSkip
                        ),
                        provider,
                        call = { it.filesApi.search }
                    )

                    val next = resp.next
                    if (next != null) newNextByProvider[provider] = next

                    resp.items
                }.getOrDefault(emptyList())
            }

            for (result in allResults) {
                val normalizedPath = result.id.normalize()
                val components = normalizedPath.components()
                if (components.size < 2) continue

                collectionIds.add(components[0])
                val parent = normalizedPath.parent()
                partialsByParent[parent] = (partialsByParent[parent] ?: emptyList()) + result
            }

            // Verify that users are allowed to read results produced by provider
            val collections = fileCollections.retrieveBulk(
                actorAndProject,
                collectionIds,
                listOf(Permission.READ),
                requireAll = false,
                ctx = session
            )

            val results = partialsByParent.flatMap { (parent, files) ->
                val collection = collections.find { it.id == parent.components()[0] }
                if (collection != null) {
                    val metadata = if (request.flags.includeMetadata == true) {
                        metadataCache.get(Pair(actorAndProject, parent))
                    } else {
                        null
                    }

                    files.map { it.toUFile(collection, metadata) }
                } else emptyList()
            }

            PageV2(
                request.normalize().itemsPerPage,
                results,
                newNextByProvider.map { it.key + HACKY_SEARCH_SEPARATOR + it.value }
                    .joinToString(HACKY_SEARCH_SEPARATOR)
            )
        }
    }

    private suspend fun loadResourceOwner(actorAndProject: ActorAndProject, ctx: DBContext = db): ResourceOwner {
        return ctx.withSession { session ->
            val owner = ResourceOwner(actorAndProject.actor.safeUsername(), actorAndProject.project)

            val isMemberOfProject = if (actorAndProject.project == null) {
                true
            } else {
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        select true
                        from project.project_members pm
                        where pm.username = :username and pm.project_id = :project
                    """
                ).rows.isNotEmpty()
            }

            if (!isMemberOfProject) {
                throw RPCException("Bad project supplied", HttpStatusCode.Forbidden)
            }

            owner
        }
    }

    private suspend fun remapSearchResults(
        actorAndProject: ActorAndProject,
        files: List<PartialUFile>,
        includeMetadata: Boolean = true,
        ctx: DBContext = db
    ): List<UFile> {
        val collectionIds = HashSet<String>()
        val partialsByParent = HashMap<String, List<PartialUFile>>()

        for (file in files) {
            val normalizedPath = file.id.normalize()
            val components = normalizedPath.components()
            if (components.size < 2) continue

            collectionIds.add(components[0])
            val parent = normalizedPath.parent()
            partialsByParent[parent] =
                (partialsByParent[parent] ?: emptyList()) + file
        }

        // Verify that users are allowed to read results produced by provider
        val collections = fileCollections.retrieveBulk(
            actorAndProject,
            collectionIds,
            listOf(Permission.READ),
            requireAll = false,
            ctx = ctx
        )

        return partialsByParent.flatMap { (parent, files) ->
            val collection = collections.find { it.id == parent.components()[0] }
            if (collection != null) {
                val metadata = if (includeMetadata) {
                    metadataCache.get(Pair(actorAndProject, parent))
                } else {
                    null
                }

                files.map { it.toUFile(collection, metadata) }
            } else emptyList()
        }
    }

    private suspend fun retrieveRelevantProviders(actorAndProject: ActorAndProject): List<String> {
        return AccountingV2.findRelevantProviders.call(
            bulkRequestOf(
                AccountingV2.FindRelevantProviders.RequestItem(
                    actorAndProject.actor.safeUsername(),
                    actorAndProject.project,
                    true,
                    ProductType.STORAGE
                )
            ),
            serviceClient
        ).orThrow()
            .responses
            .flatMap { it.providers }
            .toSet()
            .toList()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun streamingSearch(
        handler: CallHandler<FilesStreamingSearchRequest, FilesStreamingSearchResult, CommonErrorMessage>
    ): Unit = with(handler) {
        return db.withSession { session ->
            val owner = loadResourceOwner(actorAndProject, session)

            val currentFolder = request.currentFolder
            val collection = if (currentFolder != null) {
                try {
                    fileCollections.retrieve(
                        actorAndProject,
                        currentFolder.components().getOrNull(0)
                            ?: throw RPCException("Current folder is unknown", HttpStatusCode.NotFound),
                        FileCollectionIncludeFlags(),
                    )
                } catch (ex: RPCException) {
                    if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                        throw RPCException("Current folder is unknown", HttpStatusCode.NotFound)
                    }
                    throw ex
                }
            } else {
                null
            }

            val relevantProviders = if (collection != null) {
                listOf(collection.specification.product.provider)
            } else {
                retrieveRelevantProviders(actorAndProject)
            }

            data class SearchSummary(
                val category: ProductCategoryId,
                val streaming: Boolean,
                val nonStreaming: Boolean
            )

            val searchSupportByProviders = HashMap<String, ArrayList<SearchSummary>>()
            run {
                val supportByProviders = relevantProviders.associateWith {
                    providerSupport.retrieveProviderSupport(it)
                }

                for ((provider, supportList) in supportByProviders) {
                    supportList.groupBy { it.product.category }.map { (category, entries) ->
                        val supportInCategory = entries.map { it.support }
                        val supportsNonStreaming = supportInCategory.any { it.files.searchSupported }
                        val supportsStreaming = supportInCategory.any { it.files.streamingSearchSupported }

                        searchSupportByProviders.getOrPut(provider) { ArrayList() }
                            .add(SearchSummary(category, supportsStreaming, supportsNonStreaming))
                    }
                }
            }

            coroutineScope {
                val jobs = searchSupportByProviders.flatMap { (provider, supportList) ->
                    supportList.mapNotNull { support ->
                        if (support.streaming) {
                            launch {
                                runCatching {
                                    providers.invokeSubscription(
                                        provider,
                                        actorAndProject,
                                        { it.filesApi.streamingSearch },
                                        FilesProviderStreamingSearchRequest(
                                            request.query,
                                            owner,
                                            request.flags,
                                            support.category,
                                            request.currentFolder
                                        ),
                                        signedIntentFromEndUser = actorAndProject.signedIntentFromUser,
                                        handler = { message ->
                                            if (message !is FilesProviderStreamingSearchResult.Result) return@invokeSubscription
                                            val results = remapSearchResults(
                                                actorAndProject,
                                                message.batch,
                                                includeMetadata = request.flags.includeMetadata == true
                                            )
                                            sendWSMessage(FilesStreamingSearchResult.Result(results))
                                        }
                                    )
                                }
                            }
                        } else if (support.nonStreaming) {
                            launch {
                                runCatching {
                                    var next: String? = null
                                    while (isActive) {
                                        val page = providers.invokeCall(
                                            provider,
                                            actorAndProject,
                                            { it.filesApi.search },
                                            FilesProviderSearchRequest(
                                                request.query,
                                                owner,
                                                request.flags,
                                                support.category,
                                                itemsPerPage = 250,
                                                next = next,
                                            ),
                                            signedIntentFromEndUser = actorAndProject.signedIntentFromUser
                                        )

                                        sendWSMessage(
                                            FilesStreamingSearchResult.Result(
                                                remapSearchResults(
                                                    actorAndProject,
                                                    page.items,
                                                    request.flags.includeMetadata == true
                                                )
                                            )
                                        )

                                        next = page.next ?: break
                                    }
                                }
                            }
                        } else {
                            null
                        }
                    }
                }

                val deadline = Time.now() + 60_000
                while (isActive) {
                    select {
                        for (job in jobs) {
                            job.onJoin {}
                        }

                        onTimeout(1000) {}
                    }

                    var allInactive = true
                    for (job in jobs) {
                        allInactive = allInactive && !job.isActive
                    }

                    if (allInactive) break

                    if (Time.now() > deadline) {
                        for (job in jobs) if (job.isActive) job.cancel()
                        break
                    }
                }
            }
        }
    }

    suspend fun move(
        actorAndProject: ActorAndProject,
        request: FilesMoveRequest
    ): FilesMoveResponse {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection, FilesMoveRequestItem,
                    BulkRequest<FilesProviderMoveRequestItem>, LongRunningTask>() {
                val oldCollectionsByRequest =
                    HashMap<FilesMoveRequestItem, RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>()
                val newCollectionsByRequest =
                    HashMap<FilesMoveRequestItem, RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>()
                override val isUserRequest = true
                override fun retrieveCall(comms: StorageCommunication) = comms.filesApi.move

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<FilesMoveRequestItem>
                ): List<RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>> {
                    val oldCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.EDIT) {
                        extractPathMetadata(it.oldId).collection
                    }
                    val newCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.EDIT) {
                        extractPathMetadata(it.newId).collection
                    }
                    for (item in oldCollections) oldCollectionsByRequest[item.first] = item
                    for (item in newCollections) newCollectionsByRequest[item.first] = item

                    val oldProviders = oldCollections.map { it.second.resource.specification.product.provider }
                    val newProviders = newCollections.map { it.second.resource.specification.product.provider }
                    for ((old, new) in oldProviders.zip(newProviders)) {
                        if (old != new) {
                            throw RPCException(
                                "Cannot move files between providers. Try a different target drive.",
                                HttpStatusCode.BadRequest
                            )
                        }
                    }

                    return newCollections
                }

                override suspend fun verifyRequest(
                    request: FilesMoveRequestItem,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (support.files.isReadOnly) {
                        throw RPCException(
                            "File-system is read only and cannot be modified",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>
                ): BulkRequest<FilesProviderMoveRequestItem> {
                    return BulkRequest(resources.map { (req, res) ->
                        val oldCollection =
                            (oldCollectionsByRequest[req]!!.second as ProductRefOrResource.SomeResource).resource
                        val newCollection =
                            (newCollectionsByRequest[req]!!.second as ProductRefOrResource.SomeResource).resource

                        FilesProviderMoveRequestItem(
                            oldCollection,
                            newCollection,
                            req.oldId,
                            req.newId,
                            req.conflictPolicy
                        )
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesMoveRequestItem, FileCollection>>,
                    response: BulkResponse<LongRunningTask?>
                ) {
                    val batch = ArrayList<FilesMoveRequestItem>()
                    for ((index, res) in resources.withIndex()) {
                        if (response.responses[index] != null) {
                            batch.add(res.first)
                        }
                    }

                    if (batch.isNotEmpty()) {
                        moveHandlers.forEach { handler -> handler(batch) }
                    }

                    registerTasks(
                        findTasksInBackgroundFromResponse(response)
                            .map { TaskToRegister(provider, "Moving files", it, actorAndProject) }
                            .toList()
                    )
                }
            }
        )
    }

    suspend fun copy(
        actorAndProject: ActorAndProject,
        request: FilesCopyRequest
    ): FilesCopyResponse {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection, FilesCopyRequestItem,
                    BulkRequest<FilesProviderCopyRequestItem>, LongRunningTask>() {
                val oldCollectionsByRequest =
                    HashMap<FilesCopyRequestItem, RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>>()
                val newCollectionsByRequest =
                    HashMap<FilesCopyRequestItem, RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>>()
                override val isUserRequest = true
                override fun retrieveCall(comms: StorageCommunication) = comms.filesApi.copy

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<FilesCopyRequestItem>
                ): List<RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>> {
                    val oldCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.READ) {
                        extractPathMetadata(it.oldId).collection
                    }
                    val newCollections = verifyAndFetchByIdable(actorAndProject, request, Permission.EDIT) {
                        extractPathMetadata(it.newId).collection
                    }
                    for (item in oldCollections) oldCollectionsByRequest[item.first] = item
                    for (item in newCollections) newCollectionsByRequest[item.first] = item

                    val oldProviders = oldCollections.map { it.second.resource.specification.product.provider }
                    val newProviders = newCollections.map { it.second.resource.specification.product.provider }
                    for ((old, new) in oldProviders.zip(newProviders)) {
                        if (old != new) {
                            throw RPCException(
                                "Cannot move files between providers. Try a different target drive.",
                                HttpStatusCode.BadRequest
                            )
                        }
                    }

                    return newCollections
                }

                override suspend fun verifyRequest(
                    request: FilesCopyRequestItem,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (support.files.isReadOnly) {
                        throw RPCException(
                            "File-system is read only and cannot be modified",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>>
                ): BulkRequest<FilesProviderCopyRequestItem> {
                    return BulkRequest(resources.map { (req, res) ->
                        val oldCollection =
                            (oldCollectionsByRequest[req]!!.second as ProductRefOrResource.SomeResource).resource
                        val newCollection =
                            (newCollectionsByRequest[req]!!.second as ProductRefOrResource.SomeResource).resource

                        FilesProviderCopyRequestItem(
                            oldCollection,
                            newCollection,
                            req.oldId,
                            req.newId,
                            req.conflictPolicy
                        )
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<FilesCopyRequestItem, FileCollection>>,
                    response: BulkResponse<LongRunningTask?>
                ) {
                    registerTasks(
                        findTasksInBackgroundFromResponse(response)
                            .map { TaskToRegister(provider, "Copying files", it, actorAndProject) }
                            .toList()
                    )
                }
            }
        )
    }

    suspend fun createUpload(
        actorAndProject: ActorAndProject,
        request: FilesCreateUploadRequest
    ): FilesCreateUploadResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.EDIT,
            { it.filesApi.createUpload },
            { extractPathMetadata(it.id).collection },
            { req, coll ->
                FilesProviderCreateUploadRequestItem(
                    coll,
                    req.id,
                    req.type,
                    req.supportedProtocols,
                    req.conflictPolicy
                )
            },
            afterCall = { provider, _, response ->
                response.responses.forEach { resp ->
                    if (resp != null) {
                        val providerSpec = providers.prepareCommunication(provider).provider
                        resp.endpoint = providerSpec.addProviderInfoToRelativeUrl(resp.endpoint)
                    }
                }
            }
        )
    }

    suspend fun createDownload(
        actorAndProject: ActorAndProject,
        request: FilesCreateDownloadRequest
    ): FilesCreateDownloadResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.READ,
            { it.filesApi.createDownload },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderCreateDownloadRequestItem(coll, req.id) },
            afterCall = { provider, _, response ->
                response.responses.forEach { resp ->
                    if (resp != null) {
                        val providerSpec = providers.prepareCommunication(provider).provider
                        resp.endpoint = providerSpec.addProviderInfoToRelativeUrl(resp.endpoint)
                    }
                }
            }
        )
    }

    suspend fun createFolder(
        actorAndProject: ActorAndProject,
        request: FilesCreateFolderRequest
    ): FilesCreateFolderResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.EDIT,
            { it.filesApi.createFolder },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderCreateFolderRequestItem(coll, req.id, req.conflictPolicy) },
            afterCall = { provider, _, response ->
                registerTasks(
                    findTasksInBackgroundFromResponse(response)
                        .map { TaskToRegister(provider, "Creating folders", it, actorAndProject) }
                        .toList()
                )
            }
        )
    }

    private fun findTasksInBackgroundFromResponse(response: BulkResponse<LongRunningTask?>) =
        response.responses
            .asSequence()
            .filterNotNull()
            .filterIsInstance<LongRunningTask.ContinuesInBackground>()

    suspend fun trash(
        actorAndProject: ActorAndProject,
        request: FilesTrashRequest
    ): FilesTrashResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.EDIT,
            { it.filesApi.trash },
            { extractPathMetadata(it.id).collection },
            { req, coll -> FilesProviderTrashRequestItem(coll, req.id) },
            afterCall = { provider, resources, response ->

                val batch = ArrayList<FindByPath>()
                for ((index, res) in resources.withIndex()) {
                    if (response.responses[index] != null) {
                        batch.add(res.first)
                    }
                }

                if (batch.isNotEmpty()) {
                    trashHandlers.forEach { handler -> handler(batch) }
                }

                registerTasks(
                    findTasksInBackgroundFromResponse(response)
                        .map { TaskToRegister(provider, "Moving to trash", it, actorAndProject) }
                        .toList()
                )
            }
        )
    }

    suspend fun emptyTrash(
        actorAndProject: ActorAndProject,
        request: FilesEmptyTrashRequest
    ): FilesEmptyTrashResponse {
        return bulkProxyEdit(
            actorAndProject,
            request,
            Permission.EDIT,
            { it.filesApi.emptyTrash },
            { extractPathMetadata(it.id).collection },
            { req, collection -> FilesProviderEmptyTrashRequestItem(collection, req.id) },
            afterCall = { provider, resources, response ->
                registerTasks(
                    findTasksInBackgroundFromResponse(response)
                        .map { TaskToRegister(provider, "Emptying trash", it, actorAndProject) }
                        .toList()
                )
            }
        )
    }

    private suspend inline fun <R : Any> verifyAndFetchByIdable(
        actorAndProject: ActorAndProject,
        request: BulkRequest<R>,
        permission: Permission,
        idGetter: (R) -> String,
    ): List<Pair<R, ProductRefOrResource.SomeResource<FileCollection>>> {
        val uniqueCollections = request.items.map(idGetter).toSet()
        val collections = fileCollections.retrieveBulk(
            actorAndProject,
            uniqueCollections,
            listOf(permission)
        ).associateBy { it.id }

        return request.items.mapNotNull { req ->
            val coll = collections[idGetter(req)] ?: return@mapNotNull null
            req to ProductRefOrResource.SomeResource(coll)
        }
    }

    private fun dummyResource(fileId: String, collection: FileCollection): UFile = UFile(
        fileId,
        UFileSpecification(collection.id, collection.specification.product),
        0L,
        UFileStatus(),
        collection.owner,
        null
    )

    private suspend fun <Req : Any, ReqProvider : Any, Res : Any> bulkProxyEdit(
        actorAndProject: ActorAndProject,
        request: BulkRequest<Req>,
        permission: Permission,
        call: (comms: StorageCommunication) ->
        CallDescription<BulkRequest<ReqProvider>, BulkResponse<Res?>, CommonErrorMessage>,
        idFetcher: (Req) -> String,
        requestMapper: (Req, FileCollection) -> ReqProvider,
        isUserRequest: Boolean = true,
        dontTolerateReadOnly: Boolean = true,
        checkRequest: (Req, FSSupport) -> Unit = { _, _ -> },
        afterCall: suspend (
            provider: String, resources: List<RequestWithRefOrResource<Req, FileCollection>>,
            response: BulkResponse<Res?>
        ) -> Unit = { _, _, _ -> }
    ): BulkResponse<Res?> {
        return proxy.bulkProxy(
            actorAndProject,
            request,
            object : BulkProxyInstructions<StorageCommunication, FSSupport, FileCollection, Req,
                    BulkRequest<ReqProvider>, Res>() {
                override val isUserRequest = isUserRequest
                override fun retrieveCall(comms: StorageCommunication) = call(comms)

                override suspend fun verifyAndFetchResources(
                    actorAndProject: ActorAndProject,
                    request: BulkRequest<Req>
                ) = verifyAndFetchByIdable(actorAndProject, request, permission, idFetcher)

                override suspend fun verifyRequest(
                    request: Req,
                    res: ProductRefOrResource<FileCollection>,
                    support: FSSupport
                ) {
                    if (dontTolerateReadOnly && support.files.isReadOnly) {
                        throw RPCException(
                            "File-system is read only and cannot be modified",
                            HttpStatusCode.BadRequest,
                            FEATURE_NOT_SUPPORTED_BY_PROVIDER
                        )
                    }

                    checkRequest(request, support)
                }

                override suspend fun beforeCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Req, FileCollection>>
                ): BulkRequest<ReqProvider> {
                    return BulkRequest(resources.map { (req, res) ->
                        val collection = (res as ProductRefOrResource.SomeResource).resource
                        requestMapper(req, collection)
                    })
                }

                override suspend fun afterCall(
                    provider: String,
                    resources: List<RequestWithRefOrResource<Req, FileCollection>>,
                    response: BulkResponse<Res?>
                ) {
                    afterCall(provider, resources, response)
                }
            }
        )
    }

    private data class TaskToRegister(
        val provider: String,
        val title: String,
        val backgroundTask: LongRunningTask.ContinuesInBackground,
        val actorAndProject: ActorAndProject
    )

    private suspend fun registerTasks(
        tasks: List<TaskToRegister>
    ) {
        if (tasks.isEmpty()) return
        data class MappedTask(val provider: String, val providerId: String, val taskId: String)

        val mappedTasks = ArrayList<MappedTask>()
        for (task in tasks) {
            val taskId = Tasks.create.call(
                CreateRequest(task.title, task.actorAndProject.actor.safeUsername()),
                serviceClient
            ).orNull()?.jobId

            if (taskId == null) {
                log.info("Unable to register tasks!")
                continue
            }

            mappedTasks.add(MappedTask(task.provider, task.backgroundTask.taskId, taskId))
        }

        if (mappedTasks.isEmpty()) return

        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val providers by parameterList<String>()
                    val providerGeneratedIds by parameterList<String>()
                    val taskIds by parameterList<String>()
                    for (task in mappedTasks) {
                        providers.add(task.provider)
                        providerGeneratedIds.add(task.providerId)
                        taskIds.add(task.taskId)
                    }
                },
                """
                    insert into file_orchestrator.task_mapping (provider_id, provider_generated_task_id, actual_task_id) 
                    select unnest(:providers::text[]), unnest(:provider_generated_ids::text[]),
                           unnest(:task_ids::text[])
                """
            )
        }
    }

    suspend fun addTaskUpdate(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FilesControlAddUpdateRequestItem>
    ) {
        val providerSpec = providers.prepareCommunication(actorAndProject.actor).provider
        db.withSession { session ->
            val taskIds = lookupTaskIds(session, providerSpec, request.items.map { it.taskId })

            for ((taskId, update) in taskIds.zip(request.items)) {
                val response = Tasks.postStatus.call(
                    PostStatusRequest(TaskUpdate(taskId, messageToAppend = update.update)),
                    serviceClient
                )

                if (response !is IngoingCallResponse.Ok) {
                    log.info("Unable to add task update: $taskId")
                }
            }
        }
    }

    suspend fun markTaskAsComplete(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FilesControlMarkAsCompleteRequestItem>
    ) {
        val providerSpec = providers.prepareCommunication(actorAndProject.actor).provider
        db.withSession { session ->
            val taskIds = lookupTaskIds(session, providerSpec, request.items.map { it.taskId })

            for (taskId in taskIds) {
                val response = Tasks.markAsComplete.call(
                    MarkAsCompleteRequest(taskId),
                    serviceClient
                )

                if (response !is IngoingCallResponse.Ok) {
                    log.info("Unable to mark task as complete: $taskId")
                }
            }
        }
    }

    private suspend fun lookupTaskIds(
        session: AsyncDBConnection,
        providerSpec: ProviderSpecification,
        providerIds: List<String>
    ): List<String> {
        val taskIds = session.sendPreparedStatement(
            {
                setParameter("provider_id", providerSpec.id)
                setParameter("task_ids", providerIds)
            },
            """
                with entries as (
                    select unnest(:task_ids::text[]) task_id
                )
                select actual_task_id
                from
                    file_orchestrator.task_mapping tm join
                    entries e on tm.provider_generated_task_id = e.task_id
                where
                    tm.provider_id = :provider_id
            """
        ).rows.map { it.getString(0)!! }

        if (taskIds.size != providerIds.size) {
            throw RPCException("Request contains unknown task IDs", HttpStatusCode.NotFound)
        }
        return taskIds
    }

    suspend fun transfer(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FilesTransferRequest>,
    ): BulkResponse<FilesTransferResponse> {
        for (reqItem in request.items) {
            val sourceDrive = collectionFromPath(actorAndProject, reqItem.sourcePath, Permission.READ)
            val sourceProvider = sourceDrive.specification.product.provider
            verifyReadRequest(UFileIncludeFlags(), sourceDrive.status.resolvedSupport!!.support)

            val destinationDrive = collectionFromPath(actorAndProject, reqItem.destinationPath, Permission.EDIT)
            val destinationProvider = destinationDrive.specification.product.provider
            verifyReadRequest(UFileIncludeFlags(), destinationDrive.status.resolvedSupport!!.support)

            if (sourceProvider != "go-slurm" && sourceProvider == destinationProvider) {
                throw RPCException("Cannot transfer files between the same provider", HttpStatusCode.BadRequest)
            }

            val sourceResp = try {
                providers.invokeCall(
                    sourceProvider,
                    actorAndProject,
                    { it.filesApi.transfer },
                    FilesProviderTransferRequest.InitiateSource(reqItem.sourcePath, sourceDrive, destinationProvider),
                    actorAndProject.signedIntentFromUser,
                )
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    throw RPCException(
                        "The source provider is unwilling to perform this request",
                        HttpStatusCode.BadRequest
                    )
                }

                throw ex
            }

            if (sourceResp !is FilesProviderTransferResponse.InitiateSource) {
                throw RPCException("Malformed reply from source provider", HttpStatusCode.BadGateway)
            }

            val destResp = try {
                providers.invokeCall(
                    destinationProvider,
                    actorAndProject,
                    { it.filesApi.transfer },
                    FilesProviderTransferRequest.InitiateDestination(
                        reqItem.destinationPath,
                        destinationDrive,
                        sourceProvider,
                        sourceResp.supportedProtocols
                    ),
                    actorAndProject.signedIntentFromUser
                )
            } catch (ex: RPCException) {
                if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                    throw RPCException(
                        "The destination provider is unwilling to perform this request",
                        HttpStatusCode.BadRequest
                    )
                }

                throw ex
            }

            if (destResp !is FilesProviderTransferResponse.InitiateDestination) {
                throw RPCException("Malformed reply from destination provider", HttpStatusCode.BadGateway)
            }

            if (destResp.selectedProtocol !in sourceResp.supportedProtocols) {
                throw RPCException("Malformed reply from destination provider (unknown protocol)", HttpStatusCode.BadGateway)
            }

            val finalResp = providers.invokeCall(
                sourceProvider,
                actorAndProject,
                { it.filesApi.transfer },
                FilesProviderTransferRequest.Start(
                    sourceResp.session,
                    destResp.selectedProtocol,
                    destResp.protocolParameters
                ),
                actorAndProject.signedIntentFromUser,
            )

            if (finalResp !is FilesProviderTransferResponse.Start) {
                throw RPCException("Malformed reply from source provider", HttpStatusCode.BadGateway)
            }
        }

        return BulkResponse(request.items.map { FilesTransferResponse() })
    }

    companion object : Loggable {
        override val log = logger()

        const val HACKY_SEARCH_SEPARATOR = "@uc@"
    }
}
