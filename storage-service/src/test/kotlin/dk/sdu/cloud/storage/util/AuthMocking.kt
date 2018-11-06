package dk.sdu.cloud.storage.util

import com.auth0.jwt.interfaces.DecodedJWT
import dk.sdu.cloud.Role
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import dk.sdu.cloud.storage.http.files.TestContext

fun mockedUser(user: String = "user1", role: Role = Role.USER): DecodedJWT {
    return TestContext.tokenValidation.validate(TokenValidationMock.createTokenForUser(user, role))
}

fun <T> withAuthMock(block: () -> T): T {
    return block()
}
