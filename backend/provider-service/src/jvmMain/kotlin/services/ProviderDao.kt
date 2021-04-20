package dk.sdu.cloud.provider.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

data class InternalProvider(
    val provider: Provider,
    val claimToken: String?,
)

class ProviderDao(
    private val projects: ProjectCache,
) {
    suspend fun create(
        ctx: DBContext,
        actor: Actor,
        project: String?,
        spec: ProviderSpecification,
        claimToken: String,
    ) {
        if (project == null) {
            throw RPCException(
                "A provider must belong to a project, please set the project context before creating a new provider",
                HttpStatusCode.BadRequest
            )
        }

        if (actor != Actor.System && (actor !is Actor.User || actor.principal.role !in Roles.PRIVILEGED)) {
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
                        setParameter("created_by", actor.safeUsername())
                        setParameter("project", project)
                        setParameter("claim_token", claimToken)
                    },
                    """
                        insert into provider.providers
                        (id, domain, https, port, created_by, project, refresh_token, claim_token, public_key)
                        values
                        (:id, :domain, :https, :port, :created_by, :project, null, :claim_token, null)
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
            val (provider) = session
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
        pagination: NormalizedPaginationRequestV2,
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
                            where p.claim_token is null and p.refresh_token is not null
                        """
                    )
            },
            mapper = { _, rows ->
                rows.mapNotNull {
                    val (provider) = rowToProvider(it)
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
        publicKey: String,
    ) {
        ctx.withSession { session ->
            val (provider) = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("newToken", newToken)
                        setParameter("publicKey", publicKey)
                    },
                    """
                        update provider.providers
                        set 
                            refresh_token = :newToken,
                            claim_token = null,
                            public_key = :publicKey
                        where id = :id
                        returning *
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull()
                ?: run {
                    println("Could not find $id")
                    throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
                }

            if (!hasPermission(actor, provider.owner, provider.acl, ProviderAclPermission.EDIT)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
        }
    }

    suspend fun updateAcl(
        ctx: DBContext,
        actor: Actor,
        id: String,
        newAcl: List<ResourceAclEntry<ProviderAclPermission>>,
    ) {
        ctx.withSession { session ->
            val (provider) = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                        setParameter("newAcl", defaultMapper.encodeToString(newAcl))
                    },
                    """
                        update provider.providers
                        set acl = :newAcl::jsonb
                        where id = :id and claim_token is null
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
    ): InternalProvider = InternalProvider(
        Provider(
            result.getString("id")!!,
            ProviderSpecification(
                result.getString("id")!!,
                result.getString("domain")!!,
                result.getBoolean("https")!!,
                result.getInt("port")!!,
            ),
            result.getString("refresh_token") ?: "",
            result.getString("public_key") ?: "",
            result.getDate("created_at")!!.toDateTime().millis,
            ProviderStatus(),
            emptyList(),
            ProviderBilling(0, 0),
            ProviderOwner(
                result.getString("created_by")!!,
                result.getString("project")!!
            ),
            defaultMapper.decodeFromString(result.getString("acl")!!)
        ),
        result.getString("claim_token")
    )

    suspend fun hasPermission(
        ctx: DBContext,
        actor: Actor,
        providerId: String,
        permission: ProviderAclPermission,
    ): Boolean {
        return ctx.withSession { session ->
            val provider = retrieveProvider(session, actor, providerId)
            hasPermission(actor, provider.owner, provider.acl, permission)
        }
    }

    private suspend fun hasPermission(
        actor: Actor,
        owner: ProviderOwner,
        aclToVerifyAgainst: List<ResourceAclEntry<ProviderAclPermission>>,
        permission: ProviderAclPermission,
        canRetry: Boolean = true,
    ): Boolean {
        val project = owner.project ?: return false
        if (actor == Actor.System) return true
        val username = actor.safeUsername()
        if (projects.isAdminOfProject(project, actor)) return true
        for (entry in aclToVerifyAgainst) {
            if (!entry.permissions.contains(permission)) continue

            val memberStatus = projects.memberStatus.get(username)
            val entity = entry.entity
            if (entity is AclEntity.ProjectGroup) {
                val matches = memberStatus?.groups?.any { it.project == entity.projectId && it.group == entity.group }
                if (matches == true) {
                    return true
                }
            }
        }

        if (canRetry) {
            projects.memberStatus.remove(username)
            return hasPermission(actor, owner, aclToVerifyAgainst, permission, canRetry = false)
        } else {
            return false
        }
    }

    suspend fun findUnclaimed(ctx: DBContext): List<InternalProvider> {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {},
                    """
                        select * 
                        from provider.providers
                        where
                            created_at < now() - '5 minutes'::interval and
                            claim_token is not null
                    """
                )
                .rows
                .map { rowToProvider(it) }
        }
    }
}
