package dk.sdu.cloud.file.orchestrator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.*

class MetadataService(
    private val db: DBContext,
    private val collections: FileCollectionService,
    private val templates: MetadataTemplateNamespaces,
) {
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: FileMetadataAddMetadataRequest,
        ctx: DBContext = this.db,
    ) {
        ctx.withSession { session ->
            // NOTE(Dan): Confirm that the user, at least, has edit permissions for the collection. This doesn't
            // guarantee that the user can actually change the affected file but UCloud simply has no way of knowing
            // if this is possible. Maybe we can introduce a verification step later which is done by the provider.
            // For the initial version I think this is enough.
            val collectionIds = request.items.map { extractPathMetadata(it.id).collection }
            collections.retrieveBulk(
                actorAndProject,
                collectionIds,
                listOf(Permission.Edit),
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
                                setParameter("path", reqItem.id.normalize())
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
                            setParameter("path", reqItem.id.normalize())
                            setParameter("parent_path", reqItem.id.parent().normalize())
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
                        """
                    )
            }
        }
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
                listOf(Permission.Read),
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
                                    is_workspace_project = false
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
                listOf(Permission.Read),
                ctx = session
            )
            val metadata = HashMap<String, HashMap<String, ArrayList<FileMetadataOrDeleted>>>()
            val templates = HashMap<String, FileMetadataTemplate>()

            session
                .sendPreparedStatement(
                    {
                        setParameter("paths", fileNames?.map { normalizedParentPath + it })
                        setParameter("parent_path", parent)
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
                                d.path in (select unnest(:paths::text[]))
                            ) and
                            (
                                (
                                    d.parent_path = :parent_path and
                                    d.workspace = :username and
                                    d.is_workspace_project = false
                                ) or
                                (
                                    :project::text is not null and
                                    d.parent_path = :parent_path and
                                    d.workspace = :project and
                                    d.is_workspace_project = true
                                )
                            )
                            
                        order by d.created_at desc
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

    suspend fun delete(
        actorAndProject: ActorAndProject,
        request: FileMetadataDeleteRequest,
        ctx: DBContext = this.db
    ) {
        ctx.withSession { session ->
            for (reqItem in request.items) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("template_id", reqItem.templateId)
                        },
                        """
                            
                        """
                    )
            }
        }
    }

    companion object {
        private val jacksonMapper = ObjectMapper().registerKotlinModule()
    }
}
