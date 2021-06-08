package dk.sdu.cloud.file.orchestrator.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.fge.jsonschema.main.JsonSchemaFactory
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.provider.api.ResourceAclEntry
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.joda.time.LocalDateTime

class MetadataTemplates(
    private val db: DBContext,
    private val projects: ProjectCache,
) {
    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        request: FileMetadataTemplatesRetrieveRequest,
        ctx: DBContext = this.db,
    ): FileMetadataTemplate {
        val template = ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", request.id)
                        setParameter("version", request.version)
                    },
                    """
                            select * 
                            from file_orchestrator.metadata_templates t join metadata_template_specs mts
                                on t.id = mts.template_id
                            where
                                t.latest_version = mts.version and
                                (:version::text is null or mts.version = :version) and
                                t.id = :id
                        """
                )
                .rows
                .map { rowToTemplate(it) }
                .singleOrNull()
        } ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        if (!hasPermissions(actorAndProject, Permission.Read, listOf(template)).single()) {
            throw RPCException("You do not have permission to use this metadata template", HttpStatusCode.Forbidden)
        }

        return template
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        pagination: NormalizedPaginationRequestV2,
    ): PageV2<FileMetadataTemplate> {
        return db.paginateV2(
            actorAndProject.actor,
            pagination,
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("username", actorAndProject.actor.safeUsername())
                            setParameter("project", actorAndProject.project)
                        },
                        """
                            declare c cursor for
                            select *
                            from 
                                file_orchestrator.metadata_templates t join metadata_template_specs mts
                                    on t.id = mts.template_id
                            where
                                t.latest_version = mts.version and
                                t.deprecated = false and
                                (
                                    t.is_public or 
                                    t.project = :project or
                                    (:project::text is null and t.created_by = :username)
                                )
                            order by
                                t.id
                        """
                    )
            },
            mapper = { _, rows ->
                val templates = rows.map { rowToTemplate(it) }
                templates
                    .zip(hasPermissions(actorAndProject, Permission.Read, templates))
                    .mapNotNull { (template, hasPermission) ->
                        if (hasPermission) template else null
                    }
            }
        )
    }

    private fun rowToTemplate(row: RowData): FileMetadataTemplate = FileMetadataTemplate(
        row.getString("id")!!,
        FileMetadataTemplate.Spec(
            row.getString("id")!!,
            row.getString("title")!!,
            row.getString("version")!!,
            defaultMapper.decodeFromString(row.getString("schema")!!),
            row.getBoolean("inheritable")!!,
            row.getBoolean("require_approval")!!,
            row.getString("description")!!,
            row.getString("change_log")!!,
            row.getString("namespace_type")!!.let { FileMetadataTemplateNamespaceType.valueOf(it) },
            row.getString("ui_schema")?.let { defaultMapper.decodeFromString(it) }
        ),
        FileMetadataTemplate.Status(emptyList()),
        emptyList(),
        ResourceOwner(row.getString("created_by")!!, row.getString("project")),
        defaultMapper.decodeFromString(row.getString("acl")!!),
        (row["created_at"]!! as LocalDateTime).toDateTime().millis,
        row.getBoolean("is_public")!!
    )

    private suspend fun hasPermissions(
        actorAndProject: ActorAndProject,
        permission: FileMetadataTemplatePermission,
        batch: List<FileMetadataTemplate>,
    ): List<Boolean> {
        val result = ArrayList<Boolean>()
        val projectStatus = projects.retrieveProjectStatus(actorAndProject.actor.safeUsername())

        for (template in batch) {
            result.add(hasPermission(
                actorAndProject,
                permission,
                projectStatus,
                template.owner,
                template.public,
                template.acl
            ))
        }

        return result
    }

    private fun hasPermission(
        actorAndProject: ActorAndProject,
        permission: Permission,
        projectStatus: ProjectCache.CacheResponse,

        owner: ResourceOwner,
        public: Boolean,
        acl: List<ResourceAclEntry>,
    ): Boolean {
        val groups = projectStatus.userStatus?.groups ?: emptyList()
        val projects = projectStatus.userStatus?.membership ?: emptyList()
        return if (public && permission == Permission.Read) {
            true
        } else {
            val project = owner.project

            (project == null && owner.createdBy == actorAndProject.actor.safeUsername()) ||
                (project != null && projects.any { it.projectId == project && it.whoami.role.isAdmin() }) ||
                acl.any { aclEntry ->
                    if (permission !in aclEntry.permissions) return@any false

                    when (val entity = aclEntry.entity) {
                        is AclEntity.ProjectGroup -> {
                            groups.any { entity.group == it.group && entity.projectId == it.project }
                        }

                        is AclEntity.User -> {
                            actorAndProject.actor.safeUsername() == entity.username
                        }

                        else -> false
                    }
                }
        }
    }

    suspend fun create(
        actorAndProject: ActorAndProject,
        request: FileMetadataTemplatesCreateRequest,
        db: DBContext = this.db,
    ) {
        val projectStatus = projects.retrieveProjectStatus(actorAndProject.actor.safeUsername())
        try {
            db.withSession { session ->
                for (reqItem in request.items) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    val jacksonNode = jacksonMapper.readTree(defaultMapper.encodeToString(reqItem.schema))
                    if (!JsonSchemaFactory.byDefault().syntaxValidator.schemaIsValid(jacksonNode)) {
                        throw RPCException("Schema is not a valid JSON-schema", HttpStatusCode.BadRequest)
                    }
                }

                for (reqItem in request.items) {
                    val templateManifest = session
                        .sendPreparedStatement(
                            {
                                setParameter("id", reqItem.id)
                                setParameter("version", reqItem.version)
                                setParameter("created_by", actorAndProject.actor.safeUsername())
                                setParameter("project", actorAndProject.project)
                                setParameter("namespace_type", reqItem.namespaceType.name)
                            },
                            """
                            insert into file_orchestrator.metadata_templates values
                            (
                                :id,
                                :version,
                                :created_by,
                                :project,
                                '[]'::jsonb,
                                '[]'::jsonb,
                                :namespace_type,
                                false,
                                false,
                                now(),
                                now()
                            )
                            on conflict (id) do update
                            set 
                                latest_version = excluded.latest_version,
                                modified_at = excluded.modified_at
                            returning created_by, project, acl, namespace_type
                        """
                        )
                        .rows
                        .single()

                    val isAllowedToWrite = hasPermission(
                        actorAndProject,
                        Permission.Edit,
                        projectStatus,
                        ResourceOwner(
                            templateManifest.getString("created_by")!!,
                            templateManifest.getString("project")
                        ),
                        false,
                        defaultMapper.decodeFromString(templateManifest.getString("acl")!!)
                    )

                    if (!isAllowedToWrite) {
                        throw RPCException("Already exists or permission denied", HttpStatusCode.Forbidden)
                    }

                    val existingNamespace = FileMetadataTemplateNamespaceType.valueOf(
                        templateManifest.getString("namespace_type")!!
                    )

                    if (existingNamespace != reqItem.namespaceType) {
                        throw RPCException(
                            "The namespaceType of a template is not allowed to change",
                            HttpStatusCode.BadRequest
                        )
                    }

                    session
                        .sendPreparedStatement(
                            {
                                setParameter("id", reqItem.id)
                                setParameter("title", reqItem.title)
                                setParameter("version", reqItem.version)
                                setParameter("schema", defaultMapper.encodeToString(reqItem.schema))
                                setParameter("inheritable", reqItem.inheritable)
                                setParameter("require_approval", reqItem.requireApproval)
                                setParameter("description", reqItem.description)
                                setParameter("change_log", reqItem.changeLog)
                                setParameter("namespace_type", reqItem.namespaceType.name)
                                setParameter("ui_schema", reqItem.uiSchema?.let { defaultMapper.encodeToString(it) })
                            },
                            """
                            insert into file_orchestrator.metadata_template_specs
                            values (
                                :id,
                                :title,
                                :version,
                                :schema,
                                :inheritable,
                                :require_approval,
                                :description,
                                :change_log,
                                :namespace_type,
                                :ui_schema::jsonb,
                                now()
                            )
                        """
                        )
                }
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }
            throw ex
        }
    }

    suspend fun deprecate(
        actorAndProject: ActorAndProject,
        request: FileMetadataTemplatesDeprecateRequest,
        db: DBContext = this.db,
    ) {
        val projectStatus = projects.retrieveProjectStatus(actorAndProject.actor.safeUsername())

        db.withSession { session ->
            for (reqItem in request.items) {
                val deprecatedRow = session
                    .sendPreparedStatement(
                        {
                            setParameter("id", reqItem.id)
                        },
                        """
                            update file_orchestrator.metadata_templates
                            set deprecated = true
                            where id = :id
                            returning created_by, project, acl
                        """
                    )
                    .rows
                    .single()

                val isAllowedToWrite = hasPermission(
                    actorAndProject,
                    Permission.Edit,
                    projectStatus,
                    ResourceOwner(
                        deprecatedRow.getString("created_by")!!,
                        deprecatedRow.getString("project")
                    ),
                    false,
                    defaultMapper.decodeFromString(deprecatedRow.getString("acl")!!)
                )

                if (!isAllowedToWrite) {
                    throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
                }
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private val jacksonMapper = ObjectMapper().registerKotlinModule()
    }
}
