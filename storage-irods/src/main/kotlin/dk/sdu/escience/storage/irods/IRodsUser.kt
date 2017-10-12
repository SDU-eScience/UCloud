package dk.sdu.escience.storage.irods

import dk.sdu.escience.storage.User

class IRodsUser(username: String, val zone: String) : User(username) {
    companion object {
        fun parse(services: AccountServices, stringRepresentation: String): IRodsUser {
            val localZone = services.connectionInformation.zone
            val tokens = stringRepresentation.split('#')
            if (tokens.size > 2 || tokens.isEmpty()) throw IllegalArgumentException("Invalid user representation")
            val zone = if (tokens.size == 2) tokens[1] else localZone
            val username = tokens[1]
            return IRodsUser(username, zone)
        }
    }
}