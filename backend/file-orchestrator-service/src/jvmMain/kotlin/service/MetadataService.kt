package dk.sdu.cloud.file.orchestrator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.SimpleResourceOwner
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.joda.time.LocalDateTime
import java.util.*

class MetadataService(
    private val db: DBContext,
    private val projects: ProjectCache,
    private val templates: MetadataTemplates,
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
                val template = templates.retrieve(
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
                                setParameter("path", reqItem.path.normalize())
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
                            setParameter("path", reqItem.path.normalize())
                            setParameter("parent_path", reqItem.path.parent().normalize())
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
                        select *
                        from file_orchestrator.metadata_documents
                        where
                            is_deletion = false and
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
                        limit 10000
                    """
                )
                .rows
                .map { rowToDocument(it) }
        }
    }

    private fun rowToDocument(row: RowData): FileMetadataAttached {
        return FileMetadataAttached(
            row.getString("path")!!,
            FileMetadataDocument(
                row.getString("id")!!,
                FileMetadataDocument.Spec(
                    row.getString("template_id")!!,
                    defaultMapper.decodeFromString(row.getString("document")!!),
                    row.getString("change_log")!!
                ),
                (row["created_at"] as LocalDateTime).toDateTime().millis,
                FileMetadataDocument.Status(
                    when (val approvalType = row.getString("approval_type")!!) {
                        "pending" -> FileMetadataDocument.ApprovalStatus.Pending()
                        "not_required" -> FileMetadataDocument.ApprovalStatus.NotRequired()
                        "approved" -> FileMetadataDocument.ApprovalStatus.Approved(
                            row.getString("approval_updated_by")!!
                        )
                        "rejected" -> FileMetadataDocument.ApprovalStatus.Rejected(
                            row.getString("approval_updated_by")!!
                        )
                        else -> {
                            throw RPCException(
                                "Unknown approval type $approvalType",
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }
                ),
                emptyList(),
                SimpleResourceOwner(
                    row.getString("created_by")!!,
                    if (row.getBoolean("is_workspace_project")!!) {
                        row.getString("workspace")!!
                    } else {
                        null
                    }
                )
            )
        )
    }

    companion object {
        private val jacksonMapper = ObjectMapper().registerKotlinModule()
    }
}
