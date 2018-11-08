package dk.sdu.cloud.activity.util

import dk.sdu.cloud.Role
import dk.sdu.cloud.service.test.TokenValidationMock
import dk.sdu.cloud.service.test.createTokenForUser
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.TestApplicationRequest

fun TestApplicationRequest.setUser(username: String = "user1", role: Role = Role.USER) {
    addHeader(HttpHeaders.Authorization, "Bearer ${TokenValidationMock.createTokenForUser(username, role)}")
}
