package dk.sdu.cloud.app.orchestrator.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

class SshKeyService(
    private val db: DBContext,
    private val jobs: JobOrchestrator,
) {
    suspend fun create(
        actorAndProject: ActorAndProject,
        keys: List<SSHKey.Spec>,
        ctx: DBContext? = null,
    ): List<FindByStringId> {
        // TODO(Dan): Validate the key in some way

        return (ctx ?: db).withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("owner", actorAndProject.actor.safeUsername())
                    keys.split {
                        into("title") { it.title }
                        into("keys") { it.key }
                    }
                },
                """
                    insert into app_orchestrator.ssh_keys(owner, title, key)
                    select :owner, unnest(:title::text[]), unnest(:keys::text[])
                    returning id
                """
            ).rows.map { FindByStringId(it.getLong(0)!!.toString()) }
        }
    }

    suspend fun retrieve(
        actorAndProject: ActorAndProject,
        id: String,
        ctx: DBContext? = null,
    ): SSHKey {
        return (ctx ?: db).withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("owner", actorAndProject.actor.safeUsername())
                },
                """
                    select id, owner, provider.timestamp_to_unix(created_at), title, key
                    from app_orchestrator.ssh_keys
                    where
                        id = :id and
                        owner = :owner
                """
            ).rows.singleOrNull()?.let { mapRow(it) }
        } ?: throw RPCException(
            "This key does not exist anymore or you might lack the permissions to read it.",
            HttpStatusCode.NotFound
        )
    }

    suspend fun browse(
        actorAndProject: ActorAndProject,
        pagination: NormalizedPaginationRequestV2,
        ctx: DBContext? = null
    ): PageV2<SSHKey> {
        val itemsPerPage = pagination.itemsPerPage
        val items = (ctx ?: db).withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("next", pagination.next?.toLongOrNull())
                    setParameter("owner", actorAndProject.actor.safeUsername())
                },
                """
                    select id, owner, provider.timestamp_to_unix(created_at), title, key
                    from app_orchestrator.ssh_keys
                    where
                        owner = :owner and
                        (:next::bigint is null or id > :next::bigint)
                    order by id
                    limit $itemsPerPage
                """
            ).rows.map { mapRow(it) }
        }

        return PageV2(
            itemsPerPage,
            items,
            if (items.size < itemsPerPage) null else items.last().id
        )
    }

    suspend fun delete(
        actorAndProject: ActorAndProject,
        keys: List<FindByStringId>,
        ctx: DBContext? = null
    ) {
        (ctx ?: db).withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("ids", keys.mapNotNull { it.id.toLongOrNull() })
                    setParameter("owner", actorAndProject.actor.safeUsername())
                },
                """
                    delete from app_orchestrator.ssh_keys
                    where
                        id = some(:ids::bigint[]) and
                        owner = :owner
                """
            )
        }
    }

    suspend fun retrieveByJob(
        actorAndProject: ActorAndProject,
        jobId: String,
        pagination: NormalizedPaginationRequestV2,
        ctx: DBContext? = null,
    ): PageV2<SSHKey> {
        val (actor) = actorAndProject
        return (ctx ?: db).withSession { session ->
            if (actor is Actor.SystemOnBehalfOfUser) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            if (actor is Actor.User && actor.principal.role != Role.PROVIDER) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            val job = jobs.retrieve(
                actorAndProject,
                jobId,
                JobIncludeFlags(includeOthers = true),
                ctx = session,
                asProvider = true
            )

            val relevantUsers = HashSet<String>()
            relevantUsers.add(job.owner.createdBy)
            run {
                val projectId = job.owner.project
                val otherAcl = job.permissions!!.others!!

                if (projectId != null) {
                    session.sendPreparedStatement(
                        { setParameter("project_id", projectId) },
                        """
                            select username
                            from project.project_members
                            where
                                project_id = :project_id and
                                (role = 'PI' or role = 'ADMIN')
                        """
                    ).rows.forEach { relevantUsers.add(it.getString(0)!!) }
                }

                val relevantGroups = HashSet<String>()
                for (entry in otherAcl) {
                    if (Permission.READ !in entry.permissions) continue

                    when (val entity = entry.entity) {
                        is AclEntity.ProjectGroup -> relevantGroups.add(entity.group)
                        is AclEntity.User -> relevantUsers.add(entity.username)
                    }
                }

                if (relevantGroups.isNotEmpty()) {
                    session.sendPreparedStatement(
                        { setParameter("group_ids", relevantGroups.toList()) },
                        """
                            select username
                            from project.group_members
                            where group_id = some(:group_ids::text[])
                        """
                    ).rows.forEach { relevantUsers.add(it.getString(0)!!) }
                }
            }

            val itemsPerPage = pagination.itemsPerPage
            val keys = session.sendPreparedStatement(
                {
                    setParameter("owners", relevantUsers.toList())
                    setParameter("next", pagination.next?.toLongOrNull())
                },
                """
                    select id, owner, provider.timestamp_to_unix(created_at), title, key
                    from app_orchestrator.ssh_keys
                    where
                        owner = some(:owners::text[]) and
                        (:next::bigint is null or id > :next::bigint)
                    order by id
                    limit $itemsPerPage
                """
            ).rows.map { mapRow(it) }

            PageV2(
                itemsPerPage,
                keys,
                if (keys.size < itemsPerPage) null else keys.last().id
            )
        }
    }

    suspend fun retrieveAsProvider(
        actorAndProject: ActorAndProject,
        usernames: List<String>,
        pagination: NormalizedPaginationRequestV2,
        ctx: DBContext? = null
    ): PageV2<SSHKey> {
        val (actor) = actorAndProject
        if (actor !is Actor.User) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        if (actor.principal.role != Role.PROVIDER) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        val providerId = actor.safeUsername().removePrefix(AuthProviders.PROVIDER_PREFIX)

        return (ctx ?: db).withSession { session ->
            val filteredUsers = usernames.filter { username ->
                val providers = jobs.findRelevantProviders(
                    ActorAndProject(Actor.SystemOnBehalfOfUser(username), null),
                    useProject = false
                )

                providerId in providers
            }

            val itemsPerPage = pagination.itemsPerPage
            val keys = session.sendPreparedStatement(
                {
                    setParameter("owners", filteredUsers)
                    setParameter("next", pagination.next?.toLongOrNull())
                },
                """
                    select id, owner, provider.timestamp_to_unix(created_at), title, key
                    from app_orchestrator.ssh_keys
                    where
                        owner = some(:owners::text[]) and
                        (:next::bigint is null or id > :next::bigint)
                    order by id
                    limit $itemsPerPage
                """
            ).rows.map { mapRow(it) }

            PageV2(
                itemsPerPage,
                keys,
                if (keys.size < itemsPerPage) null else keys.last().id
            )
        }
    }

    private fun mapRow(row: RowData): SSHKey {
         return SSHKey(
            row.getLong(0)!!.toString(),
            row.getString(1)!!,
            row.getDouble(2)!!.toLong(),
            SSHKey.Spec(
                row.getString(3)!!,
                row.getString(4)!!
            )
        )
    }
}
