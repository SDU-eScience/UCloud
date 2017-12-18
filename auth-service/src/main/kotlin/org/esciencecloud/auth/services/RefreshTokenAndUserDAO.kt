package org.esciencecloud.auth.services

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

data class RefreshTokenAndUser(val associatedUser: String, val token: String)

object RefreshTokens : Table() {
    val token = varchar("token", 36).primaryKey()
    val associatedUser = varchar("associated_user", 255)
}

object RefreshTokenAndUserDAO {
    fun findById(token: String): RefreshTokenAndUser? {
        val results = transaction {
            RefreshTokens.select { RefreshTokens.token eq token }.limit(1).toList()
        }

        return results.singleOrNull()?.let {
            RefreshTokenAndUser(
                    token = it[RefreshTokens.token],
                    associatedUser = it[RefreshTokens.associatedUser]
            )
        }
    }

    fun insert(tokenAndUser: RefreshTokenAndUser): Boolean {
        transaction {
            RefreshTokens.insert {
                it[token] = tokenAndUser.token
                it[associatedUser] = tokenAndUser.associatedUser
            }
        }
        return true
    }

    fun delete(token: String): Boolean {
        return transaction {
            RefreshTokens.deleteWhere { RefreshTokens.token eq token }
        } == 1
    }
}