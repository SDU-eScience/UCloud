package dk.sdu.cloud.provider.services

import com.fasterxml.jackson.module.kotlin.readValue
import com.github.jasync.sql.db.ResultSet
import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Actor
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.paginateV2
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.safeUsername
import io.ktor.http.*

class ProviderDao(
    private val projects: ProjectCache,
) {
    suspend fun create(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        spec: ProviderSpecification,
    ) {
        if (project == null) {
            throw RPCException(
                "A provider must belong to a project, please set the project context before creating a new provider",
                HttpStatusCode.BadRequest
            )
        }

        if (actor != Actor.System && (actor !is Actor.User || actor.principal.role != Role.ADMIN)) {
            throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }

        ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", spec.id)
                        setParameter("domain", spec.domain)
                        setParameter("https", spec.https)
                        setParameter("port", spec.port ?: if (spec.https) 443 else 80)
                        setParameter("manifest", defaultMapper.writeValueAsString(spec.manifest))
                        setParameter("created_by", actor.safeUsername())
                        setParameter("project", project)
                        setParameter("refresh_token", "not-yet-initialised")
                    },
                    """
                        insert into provider.providers
                        (id, domain, https, port, manifest, created_by, project, refresh_token) 
                        values
                        (:id, :domain, :https, :port, :manifest, :created_by, :project, :refresh_token) 
                    """
                )
        }
    }

    suspend fun retrieveProvider(
        ctx: DBContext,
        actor: Actor,
        id: String,
    ): Provider {
        return ctx.withSession { session ->
            val provider = session
                .sendPreparedStatement(
                    { setParameter("id", id) },
                    """
                        select * 
                        from provider.providers p
                        where id = :id
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!hasPermission(actor, provider.owner, provider.acl, ProviderAclPermission.EDIT)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            provider
        }
    }

    suspend fun browseProviders(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        pagination: NormalizedPaginationRequestV2
    ): PageV2<Provider> {
        val isPrivileged = actor == Actor.System || (actor is Actor.User && actor.principal.role == Role.ADMIN)
        return ctx.paginateV2(
            actor,
            pagination,
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("project", project)
                        },
                        """
                            declare c cursor for
                            select *
                            from provider.providers p
                        """
                    )
            },
            mapper = { _, rows ->
                rows.mapNotNull {
                    val provider = rowToProvider(it)
                    val hasPermission = hasPermission(actor, provider.owner, provider.acl, ProviderAclPermission.EDIT)
                    if (!isPrivileged && !hasPermission) {
                        null
                    } else {
                        provider
                    }
                }
            }
        )
    }

    suspend fun updateToken(
        ctx: DBContext,
        actor: Actor,
        id: String,
        newToken: String,
    ) {
        ctx.withSession { session ->
            val provider = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("newToken", newToken)
                    },
                    """
                        update provider.providers
                        set refresh_token = :newToken::jsonb
                        where id = :id
                        returning *
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!hasPermission(actor, provider.owner, provider.acl, ProviderAclPermission.EDIT)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }
    }

    suspend fun updateAcl(
        ctx: DBContext,
        actor: Actor,
        id: String,
        newAcl: List<ProviderAclEntry>,
    ) {
        ctx.withSession { session ->
            val provider = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("newAcl", defaultMapper.writeValueAsString(newAcl))
                    },
                    """
                        update provider.providers
                        set acl = :newAcl::jsonb
                        where id = :id
                        returning *
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!hasPermission(actor, provider.owner, provider.acl, ProviderAclPermission.EDIT)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }
    }

    suspend fun updateManifest(
        ctx: DBContext,
        actor: Actor,
        id: String,
        newManifest: ProviderManifest,
    ) {
        ctx.withSession { session ->
            val provider = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("newManifest", defaultMapper.writeValueAsString(newManifest))
                    },
                    """
                        update provider.providers
                        set manifest = :newManifest::jsonb
                        where id = :id
                        returning *
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!hasPermission(actor, provider.owner, provider.acl, ProviderAclPermission.EDIT)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }
    }

    private fun rowToProvider(
        result: RowData,
    ): Provider = Provider(
        result.getString("id")!!,
        ProviderSpecification(
            result.getString("id")!!,
            result.getString("domain")!!,
            result.getBoolean("https")!!,
            result.getInt("port")!!,
            defaultMapper.readValue(result.getString("manifest")!!),
        ),
        result.getString("refresh_token")!!,
        result.getDate("created_at")!!.toDateTime().millis,
        ProviderStatus(),
        emptyList(),
        ProviderBilling(0, 0),
        ProviderOwner(
            result.getString("created_by")!!,
            result.getString("project")!!
        ),
        defaultMapper.readValue(result.getString("acl")!!)
    )

    suspend fun hasPermission(
        ctx: DBContext,
        actor: Actor,
        providerId: String,
        permission: ProviderAclPermission
    ): Boolean {
        return ctx.withSession { session ->
            val provider = retrieveProvider(session, actor, providerId)
            hasPermission(actor, provider.owner, provider.acl, permission)
        }
    }

    private suspend fun hasPermission(
        actor: Actor,
        owner: ProviderOwner,
        aclToVerifyAgainst: List<ProviderAclEntry>,
        permission: ProviderAclPermission,
    ): Boolean {
        val project = owner.project ?: return false
        if (actor == Actor.System) return true
        val username = actor.safeUsername()
        if (projects.isAdminOfProject(project, actor)) return true
        for (entry in aclToVerifyAgainst) {
            if (!entry.permissions.contains(permission)) continue

            val entity = entry.entity
            if (entity is AclEntity.ProjectGroup) {
                val status = projects.groupMembers.get(ProjectAndGroup(entity.projectId, entity.group))
                if (status != null && status.any { it == username }) {
                    return true
                }
            }
        }

        return false
    }
}
