package org.esciencecloud.auth

data class RefreshTokenAndUser(val associatedUser: String, val token: String)

object RefreshTokenAndUserDAO {
    private val inMemoryDb = HashMap<String, RefreshTokenAndUser>()

    fun findById(token: String): RefreshTokenAndUser? {
        return inMemoryDb[token]
    }

    fun insert(tokenAndUser: RefreshTokenAndUser): Boolean {
        if (tokenAndUser.token !in inMemoryDb) {
            inMemoryDb[tokenAndUser.token] = tokenAndUser
            return true
        }
        return false
    }

    fun delete(tokenAndUser: RefreshTokenAndUser): Boolean {
        if (tokenAndUser.token in inMemoryDb) {
            inMemoryDb.remove(tokenAndUser.token)
            return true
        }
        return false
    }
}