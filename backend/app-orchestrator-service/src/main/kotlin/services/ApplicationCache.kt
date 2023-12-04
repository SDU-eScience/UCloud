package dk.sdu.cloud.app.orchestrator.services

import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean

class ApplicationCache(private val db: DBContext) {
    private val mutex = ReadWriterMutex()
    private val applications = HashMap<NameAndVersion, Application>()
    private val didWarmup = AtomicBoolean(false)

    suspend fun fillCache(appName: String? = null) {
        mutex.withWriter {
            db.withSession { session ->
                val rows = session.sendPreparedStatement(
                    {
                        setParameter("app_name", appName)
                    },
                    """
                        select app_store.application_to_json(app, t)
                        from
                            app_store.applications app
                            join app_store.tools t on 
                                app.tool_name = t.name 
                                and app.tool_version = t.version
                        where
                            :app_name::text is null
                            or app.name = :app_name
                    """
                ).rows
                for (row in rows) {
                    val app = defaultMapper.decodeFromString(Application.serializer(), row.getString(0)!!)
                    val key = NameAndVersion(app.metadata.name, app.metadata.version)
                    applications[key] = app
                }
            }
        }
    }

    suspend fun invalidate(name: String, version: String) {
        mutex.withWriter { applications.remove(NameAndVersion(name, version)) }
    }

    suspend fun resolveApplication(name: String, version: String, allowLookup: Boolean = true): Application? {
        return resolveApplication(NameAndVersion(name, version), allowLookup)
    }

    suspend fun resolveApplication(nameAndVersion: NameAndVersion, allowLookup: Boolean = true): Application? {
        if (didWarmup.compareAndSet(false, true)) fillCache()

        val result = mutex.withReader { applications[nameAndVersion] }
        if (result != null) return result
        if (!allowLookup) return null

        fillCache(nameAndVersion.name)
        return resolveApplication(nameAndVersion, allowLookup = false)
    }
}
