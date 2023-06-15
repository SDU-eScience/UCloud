package dk.sdu.cloud.auth.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable

class PersonService(
    private val passwordHashingService: PasswordHashingService,
    private val usernameGenerator: UniqueUsernameService
) {
    fun createUserByPassword(
        firstNames: String,
        lastName: String,
        username: String,
        role: Role,
        password: String,
        email: String,
        twoFactorAuthentication: Boolean = false,
        organization: String? = null,
    ): Person.ByPassword {
        if (username.contains(Regex("[\\\\?\\/!\$%^&*)(\\[\\]}{':;\\r?\\n]+"))) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Username contains illegal chars")
        }
        val (hashed, salt) = passwordHashingService.hashPassword(password)
        return Person.ByPassword(
            id = username,
            role = role,
            title = null,
            firstNames = firstNames,
            lastName = lastName,
            phoneNumber = null,
            orcId = null,
            email = email,
            password = hashed,
            salt = salt,
            twoFactorAuthentication = twoFactorAuthentication,
            serviceLicenseAgreement = 0,
            organizationId = organization,
        )
    }

    suspend fun createUserByWAYF(
        id: String,
        firstNames: String,
        lastNames: String,
        organization: String?,
        email: String,
    ): Person.ByWAYF {
        return Person.ByWAYF(
            id = usernameGenerator.generateUniqueName("$firstNames$lastNames".replace(" ", "")),
            wayfId = id,
            firstNames = firstNames,
            lastName = lastNames,
            role = Role.USER,
            title = null,
            phoneNumber = null,
            orcId = null,
            email = email,
            organizationId = organization ?: "",
            serviceLicenseAgreement = 0
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
