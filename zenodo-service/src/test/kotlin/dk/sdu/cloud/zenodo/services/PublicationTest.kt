package dk.sdu.cloud.zenodo.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.Role
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.client.RESTResponse
import dk.sdu.cloud.metadata.utils.withAuthMock
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.storage.api.FileDescriptions
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.zenodo.http.Controller
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.client.response.HttpResponse
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationRequest
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import io.mockk.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class PublicationTest{

    @Test
    fun `test`(){
        println("hello")
    }
}