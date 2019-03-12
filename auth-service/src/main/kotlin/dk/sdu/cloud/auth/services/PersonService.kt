package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.saml.AttributeURIs
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode

class PersonService(
    private val passwordHashingService: PasswordHashingService,
    private val usernameGenerator: UniqueUsernameService<*>
) {
    fun createUserByPassword(
        firstNames: String,
        lastName: String,
        email: String,
        role: Role,
        password: String
    ): Person.ByPassword {
        val (hashed, salt) = passwordHashingService.hashPassword(password)
        return Person.ByPassword(
            id = email,
            role = role,
            title = null,
            firstNames = firstNames,
            lastName = lastName,
            phoneNumber = null,
            orcId = null,
            emailAddresses = listOf(email),
            preferredEmailAddress = email,
            password = hashed,
            salt = salt
        )
    }

    fun createUserByWAYF(authenticatedUser: SamlRequestProcessor): Person.ByWAYF {
        if (!authenticatedUser.authenticated) throw IllegalStateException("User is not authenticated")
        val id = authenticatedUser.attributes["eduPersonTargetedID"]?.firstOrNull()
            ?: throw IllegalArgumentException("Missing EduPersonTargetedId")
        val firstNames =
            authenticatedUser.attributes["gn"]?.firstOrNull() ?: throw IllegalArgumentException("Missing gn")
        val lastNames =
            authenticatedUser.attributes["sn"]?.firstOrNull() ?: throw IllegalArgumentException("Missing sn")
        val organization = authenticatedUser.attributes["schacHomeOrganization"]?.firstOrNull()
            ?: throw IllegalArgumentException("Missing schacHomeOrganization")

        if (organization != "sdu.dk") throw RPCException.fromStatusCode(HttpStatusCode.Forbidden)

        val role = Role.USER

        return Person.ByWAYF(
            id = usernameGenerator.generateUniqueName("$firstNames$lastNames".replace(" ", "")),
            wayfId = id,
            firstNames = firstNames,
            lastName = lastNames,
            role = role,
            title = null,
            phoneNumber = null,
            orcId = null,
            emailAddresses = emptyList(),
            preferredEmailAddress = null,
            organizationId = organization
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
