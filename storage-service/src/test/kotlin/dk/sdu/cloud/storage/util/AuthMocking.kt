package dk.sdu.cloud.storage.util

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.service.TokenValidation
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use
import java.util.*

fun stringClaim(value: String): Claim {
    val result = mockk<Claim>()
    every { result.isNull } returns false
    every { result.asString() } returns value
    return result
}

fun mockedUser(user: String = "user1", role: Role = Role.USER): DecodedJWT {
    val jwt = mockk<DecodedJWT>()
    every { jwt.subject } returns user

    val roleClaim = stringClaim(role.toString())
    val firstNameClaim = stringClaim(user)
    val lastNameClaim = stringClaim(user)
    val sessionReference = stringClaim(UUID.randomUUID().toString())

    every { jwt.getClaim("role") } returns roleClaim
    every { jwt.getClaim("firstNames") } returns firstNameClaim
    every { jwt.getClaim("lastName") } returns lastNameClaim
    every { jwt.getClaim("publicSessionReference") } returns sessionReference

    every { jwt.issuedAt } returns Date(System.currentTimeMillis())
    every { jwt.expiresAt } returns Date(System.currentTimeMillis() + (1000 * 60 * 60 * 24 * 365L))
    every { jwt.audience } returns listOf(SecurityScope.ALL_WRITE.toString())

    every { jwt.token } returns "$user/$role"
    return jwt
}

fun <T> withAuthMock(block: () -> T): T {
    return objectMockk(TokenValidation).use {
        // TODO Refactor this part
        // TODO Put this in a common place. Many services will need this
        every { TokenValidation.validate(any(), any()) } answers {
            val token = arg<String>(0).split("/")
            val user = token.first()
            val role = if (token.size == 2) Role.valueOf(token[1]) else Role.USER

            mockedUser(user, role)
        }

        every { TokenValidation.validateOrNull(any(), any()) } answers {
            val token = arg<String>(0).split("/")
            val user = token.first()
            val role = if (token.size == 2) Role.valueOf(token[1]) else Role.USER
            mockedUser(user, role)
        }

        block()
    }
}
