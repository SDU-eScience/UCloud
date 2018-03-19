package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import org.asynchttpclient.BoundRequestBuilder
import java.util.*

fun AuthenticatedCloud.withCausedBy(causedBy: String): AuthenticatedCloud {
    val delegate = this
    return object : AuthenticatedCloud {
        override val parent: CloudContext
            get() = delegate.parent

        override fun BoundRequestBuilder.configureCall() {
            //  This is syntactically confusing. But it will call the configureCall of delegate on the builder
            val builder = this
            with (delegate) { builder.configureCall() }

            setHeader("Job-Id", UUID.randomUUID().toString())
            setHeader("Caused-By", causedBy)
        }
    }
}