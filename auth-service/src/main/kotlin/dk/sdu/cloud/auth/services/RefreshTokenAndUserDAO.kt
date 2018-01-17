package dk.sdu.cloud.auth.services

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

data class RefreshTokenAndUser(val associatedUser: String, val token: String)

object RefreshTokens : Table() {
    val token = varchar("token", 36).primaryKey()
    val associatedUser = reference("associated_user", Principals.id)
}

object RefreshTokenAndUserDAO {
    private val log = LoggerFactory.getLogger(RefreshTokenAndUserDAO::class.java)

    fun findById(token: String): RefreshTokenAndUser? {
        log.debug("findById($token)")
        val results = transaction {
            RefreshTokens.select { RefreshTokens.token eq token }.limit(1).toList()
        }

        return results.singleOrNull()?.let {
            RefreshTokenAndUser(
                    token = it[RefreshTokens.token],
                    associatedUser = it[RefreshTokens.associatedUser]
            )
        }.also {
            log.debug("Returning $it")
        }
    }

    fun insert(tokenAndUser: RefreshTokenAndUser) {
        log.debug("insert($tokenAndUser)")
        transaction {
            RefreshTokens.insert {
                it[token] = tokenAndUser.token
                it[associatedUser] = tokenAndUser.associatedUser
            }
        }
    }

    fun delete(token: String): Boolean {
        log.debug("delete($token)")
        return transaction {
            RefreshTokens.deleteWhere { RefreshTokens.token eq token }
        } == 1
    }

}