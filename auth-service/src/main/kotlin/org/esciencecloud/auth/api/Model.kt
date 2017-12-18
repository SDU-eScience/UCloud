package org.esciencecloud.auth.api

import java.util.*

enum class Role {
    USER,
    ADMIN,
    SERVICE
}

data class User(
        val fullName: String,
        val email: String,
        val role: Role,
        val hashedPassword: ByteArray? = null,
        val salt: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (fullName != other.fullName) return false
        if (email != other.email) return false
        if (role != other.role) return false
        if (!Arrays.equals(hashedPassword, other.hashedPassword)) return false
        if (!Arrays.equals(salt, other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fullName.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + (hashedPassword?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (salt?.let { Arrays.hashCode(it) } ?: 0)
        return result
    }
}

