package dk.sdu.cloud.auth.utils

import com.auth0.jwt.interfaces.Claim
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.SecurityPrincipal
import dk.sdu.cloud.service.TokenValidation
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use

private fun mockedUser(user: String = "user1", role: String): SecurityPrincipal {
    val jwt = mockk<SecurityPrincipal>()
    every { jwt.subject } returns user

    val roleClaim = mockk<Claim>()
    every { roleClaim.asString() } returns role.toString()

    every { jwt.getClaim("role") } returns roleClaim

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
            val role = if (token.size == 2) token[1] else Role.USER.name

            mockedUser(user, role)
        }

        every { TokenValidation.validateOrNull(any(), any()) } answers {
            val token = arg<String>(0).split("/")
            val user = token.first()
            val role = if (token.size == 2) token[1] else Role.USER.name
            mockedUser(user, role)
        }

        block()
    }
}
