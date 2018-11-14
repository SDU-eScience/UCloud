package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.OPAQUE_TOKEN_PREFIX
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import org.slf4j.Logger
import java.security.SecureRandom
import java.util.*

class OpaqueTokenService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val opaqueAccessTokenDao: OpaqueAccessTokenDao<DBSession>
) : TokenGenerationService {
    private val secureRandom = SecureRandom()

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_LENGTH_IN_BYTES)
        secureRandom.nextBytes(bytes)
        return OPAQUE_TOKEN_PREFIX + Base64.getEncoder().encodeToString(bytes)
    }

    override fun generate(contents: AccessTokenContents): String {
        val token = generateToken()
        db.withTransaction { ctx ->
            opaqueAccessTokenDao.insert(ctx, token, contents)
        }
        return token
    }

    fun find(token: String): AccessTokenContents? {
        return db.withTransaction { ctx ->
            opaqueAccessTokenDao.find(ctx, token)
        }
    }

    fun revoke(token: String) {
        db.withTransaction { opaqueAccessTokenDao.revoke(it, token) }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        private const val TOKEN_LENGTH_IN_BYTES = 64
    }
}
