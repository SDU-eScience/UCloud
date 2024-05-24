package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.project.api.v2.FindByProjectId
import dk.sdu.cloud.project.api.v2.Projects
import dk.sdu.cloud.provider.api.AclEntity
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.ReadWriterMutex
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession

interface IIdCardService {
    suspend fun fetchAllUserGroup(pid: Int): Int
    suspend fun fetchIdCard(actorAndProject: ActorAndProject, allowCached: Boolean = true): IdCard
    suspend fun lookupUid(uid: Int): String?
    suspend fun lookupPid(pid: Int): String?
    suspend fun lookupGid(gid: Int): AclEntity.ProjectGroup?
    suspend fun lookupUidFromUsername(username: String): Int?
    suspend fun lookupGidFromGroupId(groupId: String): Int?
    suspend fun lookupPidFromProjectId(projectId: String): Int?
    suspend fun retrieveProviderProjectPid(providerId: String): Int?
    suspend fun lookupProjectInformation(pid: Int): ProjectInfo?

    data class ProjectInfo(
        val projectId: String,
        val numericPid: Int,
        val title: String,
        val canConsumeResources: Boolean,
        val parentProject: String? = null,
        val pi: String
    )

    suspend fun lookupUidFromUsernameOrFail(username: String?): Int {
        if (username == null) return 0
        return lookupUidFromUsername(username) ?: throw RPCException("Unknown user: $username", HttpStatusCode.NotFound)
    }

    suspend fun lookupPidFromProjectIdOrFail(projectId: String?): Int {
        if (projectId == null) return 0
        return lookupPidFromProjectId(projectId) ?: throw RPCException(
            "Unknown project: $projectId",
            HttpStatusCode.NotFound
        )
    }
}

class IdCardService(
    private val db: DBContext,
    private val scope: BackgroundScope,
    private val serviceClient: AuthenticatedClient,
) : IIdCardService {
    private val reverseUidCache = SimpleCache<Int, String>(maxAge = SimpleCache.DONT_EXPIRE) { uid ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("uid", uid) },
                """
                    select id 
                    from auth.principals p
                    where
                        p.uid = :uid
                """
            ).rows.singleOrNull()?.getString(0)
        }
    }

    private val reversePidCache = SimpleCache<Int, IIdCardService.ProjectInfo>(maxAge = 60_000L) { pid ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("pid", pid) },
                """
                    select id, title, can_consume_resources, parent, username
                    from project.projects p join project.project_members pm on p.id = pm.project_id
                    where
                        pm.role = 'PI' and
                        p.pid = :pid
                """
            ).rows.singleOrNull()?.let { row ->
                val id = row.getString(0)!!
                val title = row.getString(1)!!
                val canConsumeResources = row.getBoolean(2)!!
                val parentId = row.getString(3)
                val pi = row.getString(4)!!

                IIdCardService.ProjectInfo(id, pid, title, canConsumeResources, parentId, pi)
            }
        }
    }

    private val reverseGidCache = SimpleCache<Int, AclEntity.ProjectGroup>(maxAge = SimpleCache.DONT_EXPIRE) { gid ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("gid", gid) },
                """
                    select project, id 
                    from project.groups g
                    where
                        g.gid = :gid
                """
            ).rows.singleOrNull()?.let {
                AclEntity.ProjectGroup(it.getString(0)!!, it.getString(1)!!)
            }
        }
    }

    private val uidCache = SimpleCache<String, Int>(maxAge = SimpleCache.DONT_EXPIRE) { username ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("username", username) },
                """
                    select uid::int4 
                    from auth.principals p
                    where
                        p.id = :username
                """
            ).rows.singleOrNull()?.getInt(0)
        }
    }

    private val gidCache = SimpleCache<String, Int>(maxAge = SimpleCache.DONT_EXPIRE) { groupId ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("group_id", groupId) },
                """
                    select gid::int4 
                    from project.groups g
                    where
                        g.id = :group_id
                """
            ).rows.singleOrNull()?.getInt(0)
        }
    }

    private val pidCache = SimpleCache<String, Int>(maxAge = SimpleCache.DONT_EXPIRE) { projectId ->
        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("project_id", projectId) },
                """
                    select pid::int4 
                    from project.projects p
                    where
                        p.id = :project_id
                """
            ).rows.singleOrNull()?.getInt(0)
        }
    }

    private val cached = SimpleCache<String, IdCard>(maxAge = 60_000) { username ->
        db.withSession { session ->
            if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
                val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
                val rows = session.sendPreparedStatement(
                    { setParameter("provider", providerName) },
                    """
                        select p.id
                        from
                            accounting.product_categories pc
                            left join accounting.products p on pc.id = p.category
                        where
                            pc.provider = :provider
                            and p.id is not null
                    """
                ).rows

                val providerOf = IntArray(rows.size)
                for ((index, row) in rows.withIndex()) {
                    providerOf[index] = row.getLong(0)!!.toInt()
                }

                IdCard.Provider(providerName, providerOf)
            } else {
                val uid = session.sendPreparedStatement(
                    { setParameter("username", username) },
                    "select uid from auth.principals where id = :username"
                ).rows.singleOrNull()?.getInt(0) ?: error("no uid for $username?")

                // NOTE(Dan): The ACL checks use Int.MAX_VALUE as a list terminator. The terminator element is ignored
                // during the search and simply assumed to never appear in the data. To make sure this is true, we
                // assert that no user/group has that ID.
                val adminOf = session.sendPreparedStatement(
                    { setParameter("username", username) },
                    """
                        select p.pid
                        from
                            project.project_members pm join
                            project.projects p on pm.project_id = p.id
                        where 
                            pm.username = :username
                            and (pm.role = 'ADMIN' or pm.role = 'PI')
                    """
                ).rows.map { it.getInt(0)!!.also { require(it != Int.MAX_VALUE && it != 0) } }.toIntArray()

                val groups = session.sendPreparedStatement(
                    { setParameter("username", username) },
                    """
                        select g.gid
                        from
                            project.group_members gm
                            join project.groups g on gm.group_id = g.id
                        where
                            gm.username = :username
                    """
                ).rows.map { it.getInt(0)!!.also { require(it != Int.MAX_VALUE && it != 0) } }.toIntArray()

                IdCard.User(uid, groups, adminOf, 0)
            }
        }
    }

    private val providerProjectId = AsyncCache<String, String>(
        scope,
        timeToLiveMilliseconds = AsyncCache.DONT_EXPIRE,
        timeoutException = {
            throw RPCException("Failed to retrieve provider project ID: $it", HttpStatusCode.GatewayTimeout)
        },
        retrieve = { projectId ->
            Projects.retrieveProviderProjectInternal
                .call(FindByStringId(projectId), serviceClient)
                .orThrow()
                .id
        }
    )

    data class UsernameAndProject(val username: String, val project: String)

    private val projectCache = SimpleCache<UsernameAndProject, Int>(60_000L) { (username, projectId) ->
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("project_id", projectId)
                    setParameter("username", username)
                },
                """
                    select p.pid
                    from
                        project.project_members pm
                        join project.projects p on pm.project_id = p.id
                    where
                        pm.project_id = :project_id
                        and pm.username = :username
                """
            ).rows.map { it.getInt(0)!! }.singleOrNull()
        }
    }

    private val allUserGroupCache = AsyncCache<Int, Int>(
        scope,
        timeToLiveMilliseconds = 60 * 60 * 1000L,
        timeoutException = {
            throw RPCException(
                "Failed to fetch information about the project. Try again later.",
                HttpStatusCode.BadGateway
            )
        },
        retrieve = { pid ->
            fun fail(): Nothing = throw RPCException(
                "Failed to fetch information about the project. Try again later.",
                HttpStatusCode.BadGateway
            )

            val projectId = reversePidCache.get(pid)?.projectId ?: fail()
            val groupId = Projects.retrieveAllUsersGroup.call(
                bulkRequestOf(FindByProjectId(projectId)),
                serviceClient
            ).orNull()?.responses?.singleOrNull()?.id ?: fail()

            gidCache.get(groupId) ?: fail()
        }
    )

    override suspend fun fetchIdCard(actorAndProject: ActorAndProject, allowCached: Boolean): IdCard {
        if (actorAndProject.actor == Actor.System) return IdCard.System

        if (!allowCached) {
            cached.remove(actorAndProject.actor.safeUsername())
        }

        var card = cached.get(actorAndProject.actor.safeUsername())
            ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val project = actorAndProject.project
        if (project != null && card is IdCard.User) {
            val projectKey = UsernameAndProject(actorAndProject.actor.safeUsername(), project)
            if (!allowCached) {
                projectCache.remove(projectKey)
            }

            card = card.copy(
                activeProject = projectCache.get(projectKey)
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
            )
        }

        return card
    }

    override suspend fun lookupUid(uid: Int): String? {
        if (uid == 0) return null
        return reverseUidCache.get(uid)
    }

    override suspend fun lookupPid(pid: Int): String? {
        if (pid == 0) return null
        return reversePidCache.get(pid)?.projectId
    }

    override suspend fun lookupGid(gid: Int): AclEntity.ProjectGroup? {
        if (gid == 0) return null
        return reverseGidCache.get(gid)
    }

    override suspend fun lookupUidFromUsername(username: String): Int? {
        return uidCache.get(username)
    }

    override suspend fun lookupGidFromGroupId(groupId: String): Int? {
        return gidCache.get(groupId)
    }

    override suspend fun lookupPidFromProjectId(projectId: String): Int? {
        return pidCache.get(projectId)
    }

    override suspend fun fetchAllUserGroup(pid: Int): Int {
        return allUserGroupCache.retrieve(pid)
    }

    override suspend fun retrieveProviderProjectPid(providerId: String): Int? {
        val projectId = runCatching { providerProjectId.retrieve(providerId) }.getOrNull() ?: return null
        return lookupPidFromProjectId(projectId)
    }

    override suspend fun lookupProjectInformation(pid: Int): IIdCardService.ProjectInfo? {
        return reversePidCache.get(pid)
    }
}

class FakeIdCardService(val products: FakeProductCache) : IIdCardService {
    private val users = ArrayList<String>().also { it.add("") }
    private val projects = ArrayList<IIdCardService.ProjectInfo>().also { it.add(IIdCardService.ProjectInfo("", 0, "", false, pi = "")) }
    private val groups = ArrayList<Pair<Int, String>>().also { it.add(Pair(0, "")) }
    private val groupMembers = ArrayList<ArrayList<Int>>().also { it.add(ArrayList()) }
    private val admins = ArrayList<ArrayList<Int>>().also { it.add(ArrayList()) }
    private val providerProjects = HashMap<String, Int>()
    private val mutex = ReadWriterMutex()

    suspend fun markProviderProject(providerId: String, providerProjectPid: Int) {
        return mutex.withWriter {
            providerProjects[providerId] = providerProjectPid
        }
    }

    suspend fun createUser(username: String): Int {
        return mutex.withWriter {
            users.add(username)
            users.size - 1
        }
    }

    suspend fun createProject(id: String, pi: String, canConsumeResources: Boolean = true, parent: String? = null): Int {
        return mutex.withWriter {
            projects.add(IIdCardService.ProjectInfo(id, projects.size, "P${projects.size}", canConsumeResources, parent, pi = pi))
            admins.add(ArrayList())
            projects.size - 1
        }
    }

    suspend fun createGroup(project: Int, title: String): Int {
        return mutex.withWriter {
            groups.add(Pair(project, title))
            groupMembers.add(ArrayList())
            groups.size - 1
        }
    }

    suspend fun addUserToGroup(uid: Int, gid: Int) {
        mutex.withWriter {
            groupMembers[gid].add(uid)
        }
    }

    suspend fun removeUserFromGroup(uid: Int, gid: Int) {
        mutex.withWriter {
            groupMembers[gid].remove(uid)
        }
    }

    suspend fun addAdminToProject(uid: Int, pid: Int) {
        mutex.withWriter {
            admins[pid].add(uid)
        }
    }

    suspend fun removeAdminFromProject(uid: Int, pid: Int) {
        mutex.withWriter {
            admins[pid].remove(uid)
        }
    }

    suspend fun fetchProvider(providerId: String): IdCard {
        return fetchIdCard(
            ActorAndProject(
                Actor.SystemOnBehalfOfUser(AuthProviders.PROVIDER_PREFIX + providerId),
                null
            )
        )
    }

    override suspend fun fetchIdCard(actorAndProject: ActorAndProject, allowCached: Boolean): IdCard {
        val username = actorAndProject.actor.safeUsername()
        val project = actorAndProject.project

        return if (username.startsWith(AuthProviders.PROVIDER_PREFIX)) {
            val providerId = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
            val productIds = products.productProviderToProductIds(providerId) ?: emptyList()
            IdCard.Provider(providerId, productIds.toIntArray())
        } else {
            mutex.withReader {
                val uid = users.indexOf(username).also { require(it != -1) }
                val adminOf = admins.indices.filter { admins[it].contains(uid) }
                val memberOf = groupMembers.indices.filter { groupMembers[it].contains(uid) }
                val activeProject = if (project != null) projects.indexOfFirst { it.projectId == project } else 0

                IdCard.User(uid, memberOf.toIntArray(), adminOf.toIntArray(), activeProject)
            }
        }
    }

    override suspend fun lookupUid(uid: Int): String? {
        return mutex.withReader { users.getOrNull(uid) }
    }

    override suspend fun lookupPid(pid: Int): String? {
        return mutex.withReader { projects.getOrNull(pid) }?.projectId
    }

    override suspend fun lookupGid(gid: Int): AclEntity.ProjectGroup? {
        return mutex.withReader {
            val group = groups.getOrNull(gid)
            if (group == null) {
                null
            } else {
                AclEntity.ProjectGroup(
                    lookupPid(group.first)!!,
                    group.second,
                )
            }
        }
    }

    override suspend fun lookupUidFromUsername(username: String): Int? {
        return mutex.withReader { users.indexOf(username).takeIf { it != -1 } }
    }

    override suspend fun lookupGidFromGroupId(groupId: String): Int? {
        return mutex.withReader { groups.indexOfFirst { it.second == groupId }.takeIf { it != -1 } }
    }

    override suspend fun lookupPidFromProjectId(projectId: String): Int? {
        return mutex.withReader { projects.indexOfFirst { it.projectId == projectId }.takeIf { it != -1 } }
    }

    override suspend fun fetchAllUserGroup(pid: Int): Int = 1 // TODO(Dan): Missing realistic implementation

    override suspend fun retrieveProviderProjectPid(providerId: String): Int? {
        return mutex.withReader { providerProjects[providerId] }
    }

    override suspend fun lookupProjectInformation(pid: Int): IIdCardService.ProjectInfo? {
        return projects.getOrNull(pid)
    }
}
