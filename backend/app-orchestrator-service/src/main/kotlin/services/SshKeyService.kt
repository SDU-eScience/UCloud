package dk.sdu.cloud.app.orchestrator.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.provider.api.Permission
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SshKeyService(
    private val db: DBContext,
    private val jobs: JobResourceService,
    private val providers: ProviderCommunications,
) {
    suspend fun create(
        actorAndProject: ActorAndProject,
        keys: List<SSHKey.Spec>,
        ctx: DBContext? = null,
    ): List<FindByStringId> {
        for (key in keys) {
            if (validPrefixes.none { prefix -> key.key.startsWith(prefix) }) {
                throw RPCException(invalidKeyError, HttpStatusCode.BadRequest)
            }
        }

        val fingerprints = keys.map { key -> calculateFingerprint(key.key) }

        return (ctx ?: db).withSession { session ->
            val ids = session.sendPreparedStatement(
                {
                    setParameter("owner", actorAndProject.actor.safeUsername())
                    keys.split {
                        into("title") { it.title }
                        into("keys") { it.key }
                    }
                    setParameter("fingerprints", fingerprints)
                },
                """
                    insert into app_orchestrator.ssh_keys(owner, title, key, fingerprint)
                    select :owner, unnest(:title::text[]), unnest(:keys::text[]), unnest(:fingerprints::text[])
                    returning id
                """
            ).rows.map { FindByStringId(it.getLong(0)!!.toString()) }

            notifyProviders(actorAndProject, session)
            return@withSession ids
        }
    }

    private suspend fun notifyProviders(
        actorAndProject: ActorAndProject,
        ctx: DBContext,
    ) {
        ctx.withSession { session ->
            val allKeys = browse(actorAndProject, null, session)
            providers.forEachRelevantProvider(actorAndProject.copy(project = null)) { providerId ->
                runCatching {
                    providers.invokeCall(
                        providerId,
                        actorAndProject,
                        { SSHKeysProvider(it).onKeyUploaded },
                        SSHKeysProviderKeyUploaded(actorAndProject.actor.safeUsername(), allKeys.items),
                        isUserRequest = true
                    )
                }
            }
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
                    select id, owner, provider.timestamp_to_unix(created_at), title, key, fingerprint
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
        pagination: NormalizedPaginationRequestV2?,
        ctx: DBContext? = null,
    ): PageV2<SSHKey> {
        val itemsPerPage = pagination?.itemsPerPage ?: Int.MAX_VALUE
        val items = (ctx ?: db).withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("next", pagination?.next?.toLongOrNull())
                    setParameter("owner", actorAndProject.actor.safeUsername())
                },
                """
                    select id, owner, provider.timestamp_to_unix(created_at), title, key, fingerprint
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

            notifyProviders(actorAndProject, session)
        }
    }

    suspend fun retrieveByJob(
        actorAndProject: ActorAndProject,
        jobId: String,
        pagination: NormalizedPaginationRequestV2,
        ctx: DBContext? = null,
    ): PageV2<SSHKey> {
        val normalizedId = jobId.toLongOrNull() ?: return emptyPageV2()
        val (actor) = actorAndProject
        return (ctx ?: db).withSession { session ->
            if (actor is Actor.SystemOnBehalfOfUser) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            if (actor is Actor.User && actor.principal.role != Role.PROVIDER) {
                throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            }

            val job = jobs.retrieveBulk(
                actorAndProject,
                longArrayOf(normalizedId),
                Permission.PROVIDER,
            ).singleOrNull() ?: return@withSession emptyPageV2()

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
                    select id, owner, provider.timestamp_to_unix(created_at), title, key, fingerprint
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
                val providers = providers.findRelevantProviders(actorAndProject)
                providerId in providers
            }

            val itemsPerPage = pagination.itemsPerPage
            val keys = session.sendPreparedStatement(
                {
                    setParameter("owners", filteredUsers)
                    setParameter("next", pagination.next?.toLongOrNull())
                },
                """
                    select id, owner, provider.timestamp_to_unix(created_at), title, key, fingerprint
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
            row.getString(5)!!,
            SSHKey.Spec(
                row.getString(3)!!,
                row.getString(4)!!
            )
        )
    }

    private fun calculateFingerprint(key: String): String {
        if (!checkIfSshKeyGenIsAvailable()) return unableToCalculateFingerprint

        val temporaryFile = java.nio.file.Files.createTempFile("key", ".pub").toFile()
        temporaryFile.writeText(key)
        try {
            val process = ProcessBuilder().apply {
                command(
                    buildList {
                        add("ssh-keygen")
                        add("-l")
                        add("-E")
                        add("sha256")
                        add("-f")
                        add(temporaryFile.absolutePath)
                    }
                )

                redirectError(ProcessBuilder.Redirect.DISCARD)
            }.start()

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                runCatching { process.destroy() }
                throw RPCException(invalidKeyError, HttpStatusCode.BadRequest)
            }

            if (process.exitValue() != 0) {
                throw RPCException(invalidKeyError, HttpStatusCode.BadRequest)
            }

            return process.inputStream.use { ins ->
                val output = process.inputStream.readNBytes(4096)
                output.decodeToString().trim()
            }
        } finally {
            temporaryFile.delete()
        }
    }

    // 0: Unknown - No one is checking
    // 1: Unknown - Someone is checking
    // 2: Yes     - ssh-keygen is available
    // 3: No      - ssh-keygen is not available
    private val sshKeyGenAvailableState = AtomicInteger(0)
    private fun checkIfSshKeyGenIsAvailable(): Boolean {
        val currentValue = sshKeyGenAvailableState.get()
        if (currentValue == 2) return true
        if (currentValue == 3) return false
        require(currentValue in 0..1)

        if (sshKeyGenAvailableState.compareAndSet(0, 1)) {
            val process = ProcessBuilder().apply {
                command("which", "ssh-keygen")
                redirectOutput(ProcessBuilder.Redirect.DISCARD)
                redirectError(ProcessBuilder.Redirect.DISCARD)
            }.start()

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                runCatching { process.destroy() }
                sshKeyGenAvailableState.set(3)
                return false
            } else {
                val exists = process.exitValue() == 0
                sshKeyGenAvailableState.set(if (exists) 2 else 3)
                return exists
            }
        } else {
            // Someone is checking and it is not our thread. Keep spinning until we know if it is available.
            val deadline = Time.now() + 15_000
            while (Time.now() < deadline) {
                val newValue = sshKeyGenAvailableState.get()
                if (newValue == 2) return true
                if (newValue == 3) return false

                Thread.sleep(15)
            }

            error("ssh-keygen check did not complete within deadline!")
        }
    }

    companion object {
        val validPrefixes = listOf(
            "ecdsa-sha2-nistp256",
            "ecdsa-sha2-nistp384",
            "ecdsa-sha2-nistp521",

            "sk-ecdsa-sha2-nistp256@openssh.com",
            "sk-ssh-ed25519@openssh.com",

            "ssh-ed25519",
            "ssh-rsa",
        )

        const val invalidKeyError = "Invalid key specified. Please check that your key is valid."
        const val unableToCalculateFingerprint = "Key fingerprint is unavailable"
    }
}
