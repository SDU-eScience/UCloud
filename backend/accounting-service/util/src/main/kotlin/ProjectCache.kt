package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.service.DistributedState
import dk.sdu.cloud.service.DistributedStateFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.serialization.Serializable
import dk.sdu.cloud.service.Loggable

@Serializable
data class MembershipStatusCacheEntry(
    val username: String,
    val adminInProjects: List<String>,
    val groupMemberOf: List<GroupMemberOf>,
) {
    @Serializable
    data class GroupMemberOf(val project: String, val group: String)
}

interface IProjectCache {
    suspend fun lookup(username: String): MembershipStatusCacheEntry
    suspend fun invalidate(username: String)
}

class ProjectCache(
    private val state: DistributedStateFactory,
    private val db: DBContext,
) : IProjectCache {
    override suspend fun lookup(username: String): MembershipStatusCacheEntry {
        val data = state(username)
        val currentState = data.get()
        if (currentState != null) return currentState

        val cacheEntry = db.withSession { session ->
            val adminInProjects = session.sendPreparedStatement(
                {
                    setParameter("username", username)
                },
                """
                    select pm.project_id
                    from
                        project.project_members pm
                    where
                        pm.username = :username and
                        (pm.role = 'ADMIN' or pm.role = 'PI')
                """
            ).rows.map { it.getString(0)!! }

            val groupMemberOf = session.sendPreparedStatement(
                {
                    setParameter("username", username)
                },
                """
                    select g.project, gm.group_id 
                    from
                        project.group_members gm join
                        project.groups g on gm.group_id = g.id
                    where
                        gm.username = :username
                """
            ).rows.map { MembershipStatusCacheEntry.GroupMemberOf(it.getString(0)!!, it.getString(1)!!) }

            MembershipStatusCacheEntry(username, adminInProjects, groupMemberOf)
        }

        data.set(cacheEntry)
        return cacheEntry
    }

    override suspend fun invalidate(username: String) {
        try {
            state(username).delete()
        } catch (ex: Throwable) {
            log.warn("Failed to invalidate project cache entry for $username\n${ex.stackTraceToString()}")
        }
    }

    private fun state(username: String): DistributedState<MembershipStatusCacheEntry> {
        return state.create(MembershipStatusCacheEntry.serializer(), "project-cache-$username", expiry = 1000L * 60 * 5)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
