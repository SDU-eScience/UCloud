package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import org.asynchttpclient.BoundRequestBuilder
import java.util.*

fun AuthenticatedCloud.withCausedBy(causedBy: String): AuthenticatedCloud {
    val delegate = this
    return object : AuthenticatedCloud {
        override val parent: CloudContext
            get() = delegate.parent

        override fun HttpRequestBuilder.configureCall() {
            //  This is syntactically confusing. But it will call the configureCall of delegate on the builder
            val builder = this
            with (delegate) { builder.configureCall() }

            header("Job-Id", UUID.randomUUID().toString())
            header("Caused-By", causedBy)
        }
    }
}