package dk.sdu.cloud.auth.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.auth.services.JWTAlgorithm
import dk.sdu.cloud.auth.services.JWTFactory
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.service.TokenValidation
import io.mockk.every
import io.mockk.mockk
import io.mockk.objectMockk
import io.mockk.use

private val jwtTokenAlgorithm = JWTAlgorithm.HMAC256("foobar")
val testJwtFactory = JWTFactory(jwtTokenAlgorithm)
val testJwtVerifier = JWT.require(jwtTokenAlgorithm).build()!!

private fun stringClaim(value: String): Claim {
    val result = mockk<Claim>()
    every { result.asString() } returns value
    return result
}

fun createServiceJWTWithTestAlgorithm(
    service: String = "_service"
): DecodedJWT {
    val principal = ServicePrincipal(service, Role.SERVICE)
    return testJwtVerifier.verify(
        testJwtFactory.create(
            user = principal,
            expiresIn = 1000 * 60 * 60,
            audience = listOf(SecurityScope.ALL_WRITE)
        ).accessToken
    )
}

fun createJWTWithTestAlgorithm(
    user: String = "user1",
    role: Role,
    audience: List<SecurityScope> = listOf(SecurityScope.ALL_WRITE)
): DecodedJWT {
    val principal = PersonUtils.createUserByPassword(user, user, user, role, "apassw0rd!")
    return testJwtVerifier.verify(
        testJwtFactory.create(
            user = principal,
            expiresIn = 1000 * 60 * 60,
            audience = audience
        ).accessToken
    )
}

fun <T> withAuthMock(block: () -> T): T {
    return objectMockk(TokenValidation).use {
        every { TokenValidation.validate(any(), any()) } answers {
            val token = arg<String>(0)
            testJwtVerifier.verify(token)
        }

        every { TokenValidation.validateOrNull(any(), any()) } answers {
            val token = arg<String>(0)
            try {
                testJwtVerifier.verify(token)
            } catch (ex: Exception) {
                null
            }
        }

        block()
    }
}
