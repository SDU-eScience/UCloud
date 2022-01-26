package dk.sdu.cloud.file.orchestrator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import dk.sdu.cloud.*
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class MetadataService(
    private val db: DBContext,
    private val collections: FileCollectionService,
    private val templates: MetadataTemplateNamespaces,
) {
    suspend fun onFilesMoved(batch: List<FilesMoveRequestItem>) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val oldPaths by parameterList<String>()
                    val newPaths by parameterList<String>()
                    for (item in batch) {
                        oldPaths.add(item.oldId.normalize())
                        newPaths.add(item.newId.normalize())
                    }
                },
                """
                    with entries as (
                        select unnest(:old_paths::text[]) old_path, unnest(:new_paths::text[]) new_path
                    )
                    update file_orchestrator.metadata_documents
                    set
                        path = e.new_path || substring(path, length(e.old_path) + 1),
                        parent_path = file_orchestrator.parent_file(e.new_path || substring(path, length(e.old_path) + 1))
                    from entries e
                    where
                        (path = e.old_path or path like e.old_path || '/%');
                """
            )
        }
    }

    suspend fun onFilesDeleted(batch: List<FindByStringId>) {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    val paths by parameterList<String>()
                    for (item in batch) paths.add(item.id.normalize())
                },
                """
                    with entries as (
                        select unnest(:paths::text[]) path
                    )
                    delete from file_orchestrator.metadata_documents d
                    using entries e
                    where
                        (e.path = d.path or d.path like e.path || '/%')
                """
            )
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: FileMetadataAddMetadataRequest,
        ctx: DBContext = this.db,
    ): BulkResponse<FindByStringId> {
        val result = ArrayList<FindByStringId>()
        ctx.withSession { session ->
            // NOTE(Dan): Confirm that the user, at least, has edit permissions for the collection. This doesn't
            // guarantee that the user can actually change the affected file but UCloud simply has no way of knowing
            // if this is possible. Maybe we can introduce a verification step later which is done by the provider.
            // For the initial version I think this is enough.
            val collectionIds = request.items.map { extractPathMetadata(it.fileId).collection }
            collections.retrieveBulk(
                actorAndProject,
                collectionIds,
                listOf(Permission.EDIT),
                ctx = session,
                useProject = true
            )

            val templateVersions =
                request.items.map { FileMetadataTemplateAndVersion(it.metadata.templateId, it.metadata.version) }
            val templates = templates.retrieveTemplate(actorAndProject, BulkRequest(templateVersions), ctx = session)
                .responses.associateBy { FileMetadataTemplateAndVersion(it.namespaceId, it.version) }

            // TODO(Dan): Performance could be made a lot better by batching requests together and making fewer
            //  sql queries

            for ((index, reqItem) in request.items.withIndex()) {
                val template = templates[templateVersions[index]]
                    ?: error("`templates` value is not correct! ${templates} ${templateVersions} $index")
                val schema = jacksonMapper.readTree(defaultMapper.encodeToString(template.schema))
                val encodedDocument = defaultMapper.encodeToString(reqItem.metadata.document)
                val document = jacksonMapper.readTree(encodedDocument)

                val validationReport = JsonSchemaFactory.byDefault().validator.validate(schema, document, true)
                if (!validationReport.isSuccess) {
                    throw RPCException("Supplied metadata is not valid", HttpStatusCode.BadRequest)
                }

                val workspace = when (template.namespaceType) {
                    FileMetadataTemplateNamespaceType.PER_USER -> actorAndProject.actor.safeUsername()
                    else -> actorAndProject.project ?: actorAndProject.actor.safeUsername()
                }

                val isWorkspaceProject = actorAndProject.project != null &&
                    template.namespaceType != FileMetadataTemplateNamespaceType.PER_USER

                if (!template.requireApproval) {
                    session
                        .sendPreparedStatement(
                            {
                                setParameter("path", reqItem.fileId.normalize())
                                setParameter("workspace", workspace)
                                setParameter("is_workspace_project", isWorkspaceProject)
                            },
                            """
                                update file_orchestrator.metadata_documents
                                set latest = false
                                where
                                    path = :path and
                                    latest = true and
                                    workspace = :workspace and
                                    is_workspace_project = :is_workspace_project
                            """
                        )
                }

                session
                    .sendPreparedStatement(
                        {
                            setParameter("path", reqItem.fileId.normalize())
                            setParameter("parent_path", reqItem.fileId.parent().normalize())
                            setParameter("template_id", reqItem.metadata.templateId)
                            setParameter("template_version", template.version)
                            setParameter("document", encodedDocument)
                            setParameter("change_log", reqItem.metadata.changeLog)
                            setParameter("created_by", actorAndProject.actor.safeUsername())

                            setParameter("workspace", workspace)
                            setParameter("is_workspace_project", isWorkspaceProject)
                            setParameter(
                                "approval_type",
                                if (template.requireApproval) "pending"
                                else "not_required"
                            )
                            setParameter("is_latest", !template.requireApproval)
                        },
                        """
                            insert into file_orchestrator.metadata_documents
                            (path, parent_path, template_id, template_version, is_deletion, document, change_log,
                             created_by, workspace, is_workspace_project, latest, approval_type, created_at,
                             approval_updated_by)
                            values
                            (
                                :path,
                                :parent_path,
                                :template_id,
                                :template_version,
                                false,
                                :document,
                                :change_log,
                                :created_by,
                                :workspace,
                                :is_workspace_project,
                                :is_latest,
                                :approval_type,
                                now(),
                                null
                            )
                            returning id
                        """
                    ).rows.forEach { result.add(FindByStringId(it.getInt(0)!!.toString())) }
            }
        }
        return BulkResponse(result)
    }

    suspend fun retrieveAll(
        actorAndProject: ActorAndProject,
        parentPath: String,
        ctx: DBContext = this.db,
    ): List<FileMetadataAttached> {
        return ctx.withSession { session ->
            val normalizedPath = parentPath.normalize()
            collections.retrieveBulk(
                actorAndProject,
                listOf(extractPathMetadata(normalizedPath).collection),
                listOf(Permission.READ),
                ctx = session
            )
            session
                .sendPreparedStatement(
                    {
                        setParameter("parent_path", normalizedPath)
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        select d.path, file_orchestrator.metadata_document_to_json(d) as json
                        from file_orchestrator.metadata_documents d
                        where
                            is_deletion = false and
                            (
                                (
                                    parent_path = :parent_path and
                                    latest = true and
                                    workspace = :username and
                                    is_workspace_project = false
                                ) or
                                (
                                    :project::text is not null and
                                    parent_path = :parent_path and
                                    latest = true and
                                    workspace = :project and
                                    is_workspace_project = true
                                )
                            )
                        limit 10000
                    """
                )
                .rows
                .map {
                    FileMetadataAttached(
                        it.getString("path")!!,
                        defaultMapper.decodeFromString(it.getString("json")!!)
                    )
                }
        }
    }

    data class RetrieveWithHistory(
        val templates: Map<String, FileMetadataTemplate>,
        val metadataByFile: Map<String, Map<String, List<FileMetadataOrDeleted>>>,
    )

    suspend fun retrieveWithHistory(
        actorAndProject: ActorAndProject,
        parentPath: String,
        fileNames: List<String>? = null,
        ctx: DBContext = this.db,
    ): RetrieveWithHistory {
        val parent = parentPath.normalize()
        val normalizedParentPath = parent.removeSuffix("/") + "/"
        return ctx.withSession { session ->
            collections.retrieveBulk(
                actorAndProject,
                listOf(extractPathMetadata(parent).collection),
                listOf(Permission.READ),
                ctx = session
            )
            val metadata = HashMap<String, HashMap<String, ArrayList<FileMetadataOrDeleted>>>()
            val templates = HashMap<String, FileMetadataTemplate>()

            session
                .sendPreparedStatement(
                    {
                        setParameter("paths", fileNames?.map { normalizedParentPath + it })
                        setParameter("parents", parent.parents() + parent)
                        setParameter("username", actorAndProject.actor.safeUsername())
                        setParameter("project", actorAndProject.project)
                    },
                    """
                        select 
                            d.path,
                            file_orchestrator.metadata_document_to_json(d) as document,
                            file_orchestrator.metadata_template_to_json(ns, mt) as template
                        from 
                            file_orchestrator.metadata_documents d join 
                            file_orchestrator.metadata_template_namespaces ns on ns.resource = d.template_id join
                            file_orchestrator.metadata_templates mt
                                on ns.resource = mt.namespace and mt.uversion = d.template_version
                        where
                            (
                                :paths::text[] is null or 
                                d.path = some(:paths::text[])
                            ) and
                            (
                                (
                                    d.workspace = :username and
                                    d.is_workspace_project = false
                                ) or
                                (
                                    :project::text is not null and
                                    d.workspace = :project and
                                    d.is_workspace_project = true
                                )
                            )
                            
                        order by d.path, d.latest desc, d.created_at desc
                    """
                )
                .rows
                .forEach { row ->
                    val path = row.getString("path")!!
                    val metadataOrDeleted =
                        defaultMapper.decodeFromString<FileMetadataOrDeleted>(row.getString("document")!!)
                    val template = defaultMapper.decodeFromString<FileMetadataTemplate>(row.getString("template")!!)

                    templates[template.namespaceId] = template

                    val existing = metadata[path] ?: HashMap()
                    val existingHistory = existing[template.namespaceId] ?: ArrayList()

                    existingHistory.add(metadataOrDeleted)
                    existing[template.namespaceId] = existingHistory
                    metadata[path] = existing
                }

            RetrieveWithHistory(templates, metadata)
        }
    }

    suspend fun move(
        actorAndProject: ActorAndProject,
        request: FileMetadataMoveRequest,
        ctx: DBContext = this.db
    ) {
        ctx.withSession(remapExceptions = true) { session ->
            // NOTE(Dan): System user is allowed to move the metadata. This can be triggered by a normal user's move of
            // a file.
            if (actorAndProject.actor != Actor.System) {
                val allCollections = (request.items.map { extractPathMetadata(it.oldFileId).collection } +
                    request.items.map { extractPathMetadata(it.newFileId).collection }).toSet()

                // NOTE(Dan): Admin is required because approval status is copied as is.
                collections.retrieveBulk(actorAndProject, allCollections, listOf(Permission.ADMIN), ctx = session)
            }

            session.sendPreparedStatement(
                {
                    val oldPaths by parameterList<String>()
                    val newPaths by parameterList<String>()
                    for (reqItem in request.items) {
                        oldPaths.add(reqItem.oldFileId)
                        newPaths.add(reqItem.newFileId)
                    }
                },
                """
                    with entries as (
                        select unnest(:old_paths::text[]) as old_path, unnest(:new_paths::text[]) new_path
                    )
                    update file_orchestrator.metadata_documents
                    set path = e.new_path
                    from entries e
                    where path = e.old_path
                """
            )
        }
    }

    suspend fun delete(
        actorAndProject: ActorAndProject,
        request: FileMetadataDeleteRequest,
        ctx: DBContext = this.db
    ) {
        data class DeletionResult(
            val path: String, val templateId: Long, val docId: Int,
            val workspace: String, val approvalRequired: Boolean
        )

        ctx.withSession(remapExceptions = true) { session ->
            val deletionResult = session.sendPreparedStatement(
                {
                    setParameter("changed_by", actorAndProject.actor.safeUsername())
                    val ids by parameterList<Long>()
                    val changeLogs by parameterList<String>()
                    request.items.forEach {
                        ids.add(it.id.toLongOrNull() ?: throw RPCException("Unknown document", HttpStatusCode.NotFound))
                        changeLogs.add(it.changeLog)
                    }
                },
                """
                    insert into file_orchestrator.metadata_documents
                    (path, parent_path, template_id, template_version, is_deletion, document, change_log,
                     created_by, workspace, is_workspace_project, latest, approval_type, created_at,
                     approval_updated_by)
                     
                    with entries as (
                        select unnest(:ids::bigint[]) id, unnest(:change_logs::text[]) change_log
                    )
                    select
                        docs.path, docs.parent_path, temps.namespace, temps.uversion, true, null, e.change_log,
                        :changed_by, docs.workspace, docs.is_workspace_project, not temps.require_approval,
                        case 
                            when temps.require_approval = true then 'pending'
                            else 'not_required'
                        end, now(), null
                    from
                        entries e join
                        file_orchestrator.metadata_documents docs on e.id = docs.id join
                        file_orchestrator.metadata_templates temps on
                            docs.template_id = temps.namespace and
                            docs.template_version = temps.uversion
                    returning path, template_id, id, workspace, latest
                """
            ).rows.map {
                DeletionResult(
                    it.getString(0)!!, it.getLong(1)!!, it.getInt(2)!!, it.getString(3)!!,
                    !it.getBoolean(4)!!
                )
            }

            val collectionsAffected = deletionResult.map { extractPathMetadata(it.path).collection }
            collections.retrieveBulk(actorAndProject, collectionsAffected, listOf(Permission.EDIT), ctx = session)

            if (deletionResult.any { !it.approvalRequired }) {
                session.sendPreparedStatement(
                    {
                        val ids by parameterList<Long>()
                        val paths by parameterList<String>()
                        val newDocs by parameterList<Int>()
                        val workspaces by parameterList<String>()
                        for (res in deletionResult) {
                            if (!res.approvalRequired) {
                                ids.add(res.templateId)
                                paths.add(res.path)
                                newDocs.add(res.docId)
                                workspaces.add(res.workspace)
                            }
                        }
                    },
                    """
                        with entries as (
                            select unnest(:ids::bigint[]) template_id, unnest(:new_docs::bigint[]) doc_id,
                                   unnest(:paths::text[]) path, unnest(:workspaces::text[]) workspace
                        )
                        update file_orchestrator.metadata_documents d
                        set latest = false
                        from entries e
                        where
                            e.path = d.path and
                            e.template_id = d.template_id and
                            e.doc_id != d.id and
                            e.workspace = d.workspace
                    """
                )
            }
        }
    }

    suspend fun approve(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = this.db
    ) {
        setApproval(ctx, actorAndProject, "approved", becomesLatest = true, request)
    }

    suspend fun reject(
        actorAndProject: ActorAndProject,
        request: BulkRequest<FindByStringId>,
        ctx: DBContext = this.db
    ) {
        setApproval(ctx, actorAndProject, "rejected", becomesLatest = false, request)
    }

    private suspend fun setApproval(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        approvalType: String,
        becomesLatest: Boolean,
        request: BulkRequest<FindByStringId>
    ) {
        ctx.withSession(remapExceptions = true) { session ->
            val pathsAffected = session.sendPreparedStatement(
                {
                    setParameter("approved_by", actorAndProject.actor.safeUsername())
                    setParameter("approval_type", approvalType)
                    setParameter("latest", becomesLatest)

                    val ids by parameterList<Long>()
                    request.items.forEach {
                        ids.add(
                            it.id.toLongOrNull()
                                ?: throw RPCException("Unknown metadata document", HttpStatusCode.NotFound)
                        )
                    }
                },
                """
                    with entries as (
                        select unnest(:ids::int[]) id, true latest
                    )
                    update file_orchestrator.metadata_documents other_docs
                    set
                        latest = case
                            when :latest then other_docs.id = some(:ids::int[])
                            else other_docs.latest
                        end,
                        approval_type = case
                            when other_docs.id = some(:ids::int[]) then :approval_type
                            else other_docs.approval_type
                        end,
                        approval_updated_by = case
                            when other_docs.id = some(:ids::int[]) then :approved_by
                            else other_docs.approval_updated_by
                        end
                    from
                        entries e left join
                        file_orchestrator.metadata_documents d on true
                    where
                        d.id = e.id and
                        d.approval_type = 'pending' and
                        d.workspace = other_docs.workspace and
                        d.template_id = other_docs.template_id and
                        d.path = other_docs.path
                    returning other_docs.path
                """
            ).rows.map { it.getString(0)!! }.toSet()

            if (pathsAffected.size != request.items.size) {
                throw RPCException("Unknown metadata document", HttpStatusCode.NotFound)
            }

            val collectionsAffected = pathsAffected.map { extractPathMetadata(it).collection }
            collections.retrieveBulk(actorAndProject, collectionsAffected, listOf(Permission.ADMIN), ctx = session)
        }
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        request: FileMetadataBrowseRequest,
        ctx: DBContext = this.db,
    ): FileMetadataBrowseResponse {
        return ctx.paginateV2(
            actorAndProject.actor,
            request.normalize(),
            create = { session ->
                session.sendPreparedStatement(
                    {
                        setParameter("username", actorAndProject.actor.username)
                        setParameter("project", actorAndProject.project)
                        setParameter("version", request.filterVersion)
                        setParameter("active", request.filterActive)
                        setParameter("template", request.filterTemplate)
                    },
                    """
                    declare c cursor for
                    SELECT doc.path, file_orchestrator.metadata_document_to_json(doc) as json
                    FROM
                        file_orchestrator.metadata_documents doc JOIN
                        file_orchestrator.metadata_templates temp ON
                            doc.template_id = temp.namespace AND
                            doc.template_version = temp.uversion left join
                        project.project_members pm on
                            doc.is_workspace_project = true and
                            doc.workspace = pm.project_id and
                            doc.workspace = :project and
                            pm.username = :username
                    WHERE
                        (
                            :template::text IS NULL OR
                            temp.title = :template
                        ) AND
                        (
                            (:active = false) OR
                            (:active = doc.latest)
                        ) AND
                        (
                            (:version::text IS NULL) OR
                            (:version = doc.template_version)
                        ) AND
                        (
                            doc.workspace = :username or
                            (
                                :project::text is not null and
                                pm.username is not null
                            )
                        ) AND
                        (
                            doc.is_deletion = false
                        )
            """.trimIndent())
            },
            mapper = { _, rows ->
                rows.map { row ->
                    FileMetadataAttached(
                        row.getString("path")!!,
                        defaultMapper.decodeFromString(row.getString("json")!!)
                    )
                }
            },
        )
    }

    companion object {
        private val jacksonMapper = ObjectMapper().registerKotlinModule()
    }
}
