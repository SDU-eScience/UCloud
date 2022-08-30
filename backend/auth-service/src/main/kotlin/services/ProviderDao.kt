package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Actor
import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.*

data class InternalProvider(
    val id: String,
    val publicKey: String,
    val privateKey: String,
    val refreshToken: String,
    val claimToken: String,
)

data class ProviderKeys(
    val publicKey: String,
    val privateKey: String,
    val refreshToken: String,
    val claimToken: String,
)

class ProviderDao {
    suspend fun register(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        providerId: String,
        keys: ProviderKeys,
    ) {
        if (!hasPermissions(providerId, actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        try {
            ctx.withSession { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("id", providerId)
                            setParameter("pub_key", keys.publicKey)
                            setParameter("priv_key", keys.privateKey)
                            setParameter("refresh_token", keys.refreshToken)
                            setParameter("claim_token", keys.claimToken)
                        },
                        """
                            insert into auth.providers 
                            (id, pub_key, priv_key, refresh_token, claim_token) 
                            values 
                            (:id, :pub_key, :priv_key, :refresh_token, :claim_token) 
                        """
                    )
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
            }
            throw ex
        }
    }

    suspend fun claim(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        claimToken: String,
    ): InternalProvider {
        return ctx.withSession { session ->
            val provider = session
                .sendPreparedStatement(
                    { setParameter("claimToken", claimToken) },
                    """
                        update auth.providers
                        set did_claim = true
                        where claim_token = :claimToken
                        returning *
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

            if (!hasPermissions(provider.id, actorAndProject)) {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }

            provider
        }
    }

    suspend fun renew(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        providerId: String,
        newKeys: ProviderKeys,
    ): InternalProvider {
        if (!hasPermissions(providerId, actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", providerId)
                        setParameter("pub_key", newKeys.publicKey)
                        setParameter("priv_key", newKeys.privateKey)
                        setParameter("refresh_token", newKeys.refreshToken)
                    },
                    """
                        update auth.providers
                        set 
                            pub_key = :pub_key,
                            priv_key = :priv_key,
                            refresh_token = :refresh_token
                        where
                            id = :id
                        returning *
                    """
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun retrieve(
        ctx: DBContext,
        actorAndProject: ActorAndProject,
        providerId: String,
    ): InternalProvider {
        if (!hasPermissions(providerId, actorAndProject)) throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("providerId", providerId) },
                    "select * from auth.providers where id = :providerId"
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun retrieveByRefreshToken(
        ctx: DBContext,
        refreshToken: String,
    ): InternalProvider {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("refreshToken", refreshToken) },
                    "select * from auth.providers where refresh_token = :refreshToken"
                )
                .rows
                .map { rowToProvider(it) }
                .singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)
        }
    }

    private fun rowToProvider(row: RowData): InternalProvider = InternalProvider(
        row.getString("id")!!,
        row.getString("pub_key")!!,
        row.getString("priv_key")!!,
        row.getString("refresh_token")!!,
        row.getString("claim_token")!!
    )

    private fun hasPermissions(
        @Suppress("UNUSED_PARAMETER") providerId: String,
        actorAndProject: ActorAndProject,
    ): Boolean {
        val (actor) = actorAndProject
        return actor == Actor.System || (actor is Actor.User && actor.principal.role in Roles.PRIVILEGED)
    }
}
