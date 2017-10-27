package org.esciencecloud.storage.irods

import org.esciencecloud.storage.User

class IRodsUser(val username: String, val zone: String, private val defaultZone: String? = null) :
        User(username + '#' + zone) {

    companion object {
        fun parse(services: AccountServices, stringRepresentation: String): IRodsUser {
            val localZone = services.connectionInformation.zone
            val tokens = stringRepresentation.split('#')
            if (tokens.size > 2 || tokens.isEmpty()) throw IllegalArgumentException("Invalid user representation")
            val zone = if (tokens.size == 2) tokens[1] else localZone
            val username = tokens[0]
            return IRodsUser(username, zone)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass == User::class.java) {
            val otherUser = other as User
            val zoneToUse = defaultZone ?: zone
            return equals(IRodsUser(otherUser.name, zoneToUse))
        }

        if (javaClass != other?.javaClass) return false

        other as IRodsUser

        if (username != other.username) return false
        if (zone != other.zone) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + zone.hashCode()
        return result
    }
}