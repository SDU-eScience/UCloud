package dk.sdu.cloud.storage.util

import com.auth0.jwt.interfaces.Claim
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.SecurityPrincipal
import dk.sdu.cloud.service.TokenValidation
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use


fun <T> withAuthMock(block: () -> T): T {
    return objectMockk(TokenValidation).use {
        // TODO Refactor this part
        // TODO Put this in a common place. Many services will need this
        every { TokenValidation.validate(any(), any()) } answers {
            val token = arg<String>(0).split("/")
            val user = token.first()
            val role = if (token.size == 2) Role.valueOf(token[1]) else Role.USER

            val jwt = mockk<SecurityPrincipal>()
            every { jwt.subject } returns user

            val roleClaim = mockk<Claim>()
            every { roleClaim.asString() } returns role.toString()

            every { jwt.getClaim("role") } returns roleClaim

            jwt
        }

        every { TokenValidation.validateOrNull(any(), any()) } answers {
            val token = arg<String>(0).split("/")
            val user = token.first()
            val role = if (token.size == 2) Role.valueOf(token[1]) else Role.USER

            val jwt = mockk<SecurityPrincipal>()
            every { jwt.subject } returns user

            val roleClaim = mockk<Claim>()
            every { roleClaim.asString() } returns role.toString()

            every { jwt.getClaim("role") } returns roleClaim

            jwt
        }

        block()
    }
}
