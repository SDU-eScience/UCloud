package dk.sdu.cloud.service

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.ApplicationRequest
import io.ktor.response.respond
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.service.KtorUtils")

// TODO Some of these should probably be a feature

fun Application.installDefaultFeatures() {
    install(CallLogging)
    install(DefaultHeaders)
    install(ContentNegotiation) {
        jackson { registerKotlinModule() }
    }

    intercept(ApplicationCallPipeline.Infrastructure) {
        val uuid = call.request.headers["Job-Id"] ?: run {
            log.debug("Did not receive a valid Job-Id in the header of the request!")
            call.respond(HttpStatusCode.BadRequest)
            finish()
            return@intercept
        }
        call.request.jobId = uuid
    }
}

private val jobIdKey = AttributeKey<String>("job-id")
var ApplicationRequest.jobId: String
    get() = call.attributes[jobIdKey]
    private set(value) = call.attributes.put(jobIdKey, value)
