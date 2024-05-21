package dk.sdu.cloud.utils

import dk.sdu.cloud.*
import dk.sdu.cloud.accounting.api.WalletOwner
import dk.sdu.cloud.provider.api.ResourceOwner
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.cliffc.high_scale_lib.NonBlockingHashMap
import org.cliffc.high_scale_lib.NonBlockingHashMapLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object ActivitySystem {
    private data class Entry(val activity: AtomicLong = AtomicLong(0), val dirty: AtomicBoolean = AtomicBoolean(false))
    private val personal = NonBlockingHashMap<String, Entry>()
    private val projects = NonBlockingHashMap<String, Entry>()

    suspend fun init() {
        if (!loadedConfig.shouldRunServerCode()) return

        dbConnection.withSession { session ->
            session.prepareStatement(
                """
                    select reference_is_user, workspace_reference, last_activity
                    from activity_system
                """
            ).useAndInvoke(
                readRow = { row ->
                    val isUser = row.getBoolean(0)!!
                    val ref = row.getString(1)!!
                    val lastActivity = row.getLong(2)!!

                    if (isUser) personal[ref] = Entry(AtomicLong(lastActivity))
                    else projects[ref] = Entry(AtomicLong(lastActivity))
                }
            )
        }

        ProcessingScope.launch {
            val log = Logger("ActivitySystem")

            while (isActive) {
                val start = Time.now()
                val taskName = "activity-sync"
                Prometheus.countBackgroundTask(taskName)

                try {
                    data class SyncEntry(val isUser: Boolean, val ref: String, val activity: Long)

                    val entriesToSync = ArrayList<SyncEntry>()
                    val maps = listOf(personal, projects)
                    for (map in maps) {
                        val isUser = map == personal
                        for (entry in map) {
                            val value = entry.value
                            if (!value.dirty.get()) continue

                            val ref = entry.key
                            entriesToSync.add(SyncEntry(isUser, ref, value.activity.get()))
                        }
                    }

                    dbConnection.withSession { session ->
                        entriesToSync.chunked(500).forEach { chunk ->
                            session.prepareStatement(
                                // language=postgresql
                                """
                                    with data as (
                                        select
                                            unnest(:is_user::bool[]) is_user,
                                            unnest(:ref::text[]) ref,
                                            unnest(:activity::int8[]) activity
                                    )
                                    insert into activity_system (reference_is_user, workspace_reference, last_activity)
                                    select is_user, ref, activity
                                    from data
                                    on conflict (reference_is_user, workspace_reference) do update set
                                        last_activity = excluded.last_activity
                                """
                            )
                        }
                    }
                } catch (ex: Throwable) {
                    log.warn("Caught exception in activity system: ${ex.toReadableStacktrace()}")
                } finally {
                    val duration = Time.now() - start
                    Prometheus.measureBackgroundDuration(taskName, duration)
                    delay(30_000 - duration)
                }
            }
        }
    }

    fun trackUsagePersonal(username: String) {
        checkServerMode()

        val value = personal.getOrPut(username) { Entry() }
        value.activity.set(Time.now())
        value.dirty.set(true)
    }

    fun trackUsageProject(projectId: String) {
        checkServerMode()

        val value = projects.getOrPut(projectId) { Entry() }
        value.activity.set(Time.now())
        value.dirty.set(true)
    }

    fun trackUsageResourceOwner(owner: ResourceOwner) {
        val project = owner.project
        if (project != null) trackUsageProject(project)
        else trackUsagePersonal(owner.createdBy)
    }

    fun trackUsageWalletOwner(owner: WalletOwner) {
        if (owner is WalletOwner.Project) trackUsageProject(owner.projectId)
        else if (owner is WalletOwner.User) trackUsagePersonal(owner.username)
    }

    fun queryLastActivePersonal(username: String): Long {
        return personal.get(username)?.activity?.get() ?: 0L
    }

    fun queryLastActiveProject(projectId: String): Long {
        return projects.get(projectId)?.activity?.get() ?: 0L
    }

    fun queryLastActiveResourceOwner(owner: ResourceOwner): Long {
        val project = owner.project
        return if (project != null) queryLastActiveProject(project)
        else queryLastActivePersonal(owner.createdBy)
    }

    fun queryLastActiveWalletOwner(owner: WalletOwner): Long {
        return when (owner) {
            is WalletOwner.Project -> queryLastActiveProject(owner.projectId)
            is WalletOwner.User -> queryLastActivePersonal(owner.username)
        }
    }

    private fun checkServerMode() {
        require(loadedConfig.shouldRunServerCode()) { "This function can only be called in server mode" }
    }
}
