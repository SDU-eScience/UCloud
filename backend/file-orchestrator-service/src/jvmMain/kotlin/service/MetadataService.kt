package dk.sdu.cloud.file.orchestrator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
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
    private val projects: ProjectCache,
    private val templateService: MetadataTemplates,
) {
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun create(
        actorAndProject: ActorAndProject,
        request: FileMetadataAddMetadataRequest,
        ctx: DBContext = this.db,
    ) {
        // TODO We cannot verify if a user is actually allowed to write to any given file
        val projectStatus = projects.retrieveProjectStatus(actorAndProject.actor.safeUsername()).userStatus
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        if (actorAndProject.project != null) {
            val isMember = projectStatus.membership.any { it.projectId == actorAndProject.project }
            if (!isMember) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        ctx.withSession { session ->
            for (reqItem in request.items) {
                val template = templateService.retrieve(
                    actorAndProject,
                    FileMetadataTemplatesRetrieveRequest(reqItem.metadata.templateId),
                    session
                )

                val schema = jacksonMapper.readTree(defaultMapper.encodeToString(template.specification.schema))
                val encodedDocument = defaultMapper.encodeToString(reqItem.metadata.document)
                val document = jacksonMapper.readTree(encodedDocument)

                val validationReport = JsonSchemaFactory.byDefault().validator.validate(schema, document, true)
                if (!validationReport.isSuccess) {
                    throw RPCException("Supplied metadata is not valid", HttpStatusCode.BadRequest)
                }

                val workspace = when (template.specification.namespaceType) {
                    FileMetadataTemplateNamespaceType.PER_USER -> actorAndProject.actor.safeUsername()
                    else -> actorAndProject.project ?: actorAndProject.actor.safeUsername()
                }

                val isWorkspaceProject = actorAndProject.project != null &&
                    template.specification.namespaceType != FileMetadataTemplateNamespaceType.PER_USER

                if (!template.specification.requireApproval) {
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
                            setParameter("id", UUID.randomUUID().toString())
                            setParameter("path", reqItem.id.normalize())
                            setParameter("parent_path", reqItem.id.parent().normalize())
                            setParameter("template_id", reqItem.metadata.templateId)
                            setParameter("template_version", template.specification.version)
                            setParameter("document", encodedDocument)
                            setParameter("change_log", reqItem.metadata.changeLog)
                            setParameter("created_by", actorAndProject.actor.safeUsername())

                            setParameter("workspace", workspace)
                            setParameter("is_workspace_project", isWorkspaceProject)
                            setParameter(
                                "approval_type",
                                if (template.specification.requireApproval) "pending"
                                else "not_required"
                            )
                            setParameter("is_latest", !template.specification.requireApproval)
                        },
                        """
                            insert into file_orchestrator.metadata_documents
                            values
                            (
                                :id,
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
                                null,
                                now()
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
        // TODO We cannot verify if a user is actually allowed to write to any given file
        val projectStatus = projects.retrieveProjectStatus(actorAndProject.actor.safeUsername()).userStatus
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        if (actorAndProject.project != null) {
            val isMember = projectStatus.membership.any { it.projectId == actorAndProject.project }
            if (!isMember) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        return ctx.withSession { session ->
            val normalizedPath = parentPath.normalize()
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
                            file_orchestrator.metadata_template_to_json(mt, mts) as template
                        from 
                            file_orchestrator.metadata_documents d join 
                            metadata_template_specs mts on 
                                mts.template_id = d.template_id and 
                                mts.version = d.template_version join 
                            metadata_templates mt on mt.id = d.template_id
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

                    templates[template.id] = template

                    val existing = metadata[path] ?: HashMap()
                    val existingHistory = existing[template.id] ?: ArrayList()

                    existingHistory.add(metadataOrDeleted)
                    existing[template.id] = existingHistory
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
