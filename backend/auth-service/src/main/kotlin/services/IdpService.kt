package dk.sdu.cloud.auth.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class IdentityProviderConfiguration {
    @SerialName("wayf")
    class Wayf()
}

data class IdentityProvider(
    val title: String,
    val configuration: IdentityProviderConfiguration,
    val countsAsMultiFactor: Boolean,
    var id: Int = 0,
    var retrievedAt: Long = 0L,
)

class IdpService(
    private val db: DBContext,
) {
    private val mutex = Mutex()
    private var cacheEntries = ArrayList<IdentityProvider>()
    private var lastRenewal = 0L

    suspend fun upsert(
        title: String,
        countsAsMultiFactor: Boolean,
        config: IdentityProviderConfiguration
    ): IdentityProvider {
        db.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("title", title)
                    setParameter("counts_as_multi_factor", countsAsMultiFactor)
                    setParameter(
                        "configuration",
                        defaultMapper.encodeToString(IdentityProviderConfiguration.serializer(), config)
                    )
                },
                """
                    insert into auth.identity_providers (title, configuration, counts_as_multi_factor) 
                    values (:title, :config, :counts_as_multi_factor)
                """
            )
        }
        renewCache(force = true)
        return findByTitle(title)
    }

    suspend fun findById(id: Int): IdentityProvider {
        renewCache()
        return mutex.withLock { cacheEntries.find { it.id == id } } ?: error("Unknown IdP: id = $id")
    }

    suspend fun findByTitle(title: String): IdentityProvider {
        renewCache()
        return mutex.withLock { cacheEntries.find { it.title == title } } ?: error("Unknown IdP: title = $title")
    }

    private suspend fun renewCache(force: Boolean = false) {
        var now = Time.now()
        if (!force && lastRenewal - now < 60_000) return

        mutex.withLock {
            now = Time.now()
            if (!force && lastRenewal - now < 60_000) return

            db.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        select id, title, configuration, counts_as_multi_factor
                        from auth.identity_providers
                    """
                ).rows.mapNotNull {
                    runCatching {
                        IdentityProvider(
                            id = it.getInt(0)!!,
                            title = it.getString(1)!!,
                            configuration = defaultMapper.decodeFromString(
                                IdentityProviderConfiguration.serializer(),
                                it.getString(2)!!
                            ),
                            countsAsMultiFactor = it.getBoolean(3)!!,
                            retrievedAt = now
                        )
                    }.getOrNull()
                }
            }
        }
    }
}
