package dk.sdu.cloud.service

import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.client.CloudContext
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.pipeline.PipelineContext
import io.ktor.request.header
import io.ktor.util.AttributeKey
import org.asynchttpclient.BoundRequestBuilder
import java.util.*

class CloudClient {
    lateinit var baseCloud: AuthenticatedCloud

    fun intercept(context: PipelineContext<Unit, ApplicationCall>): Unit = with(context) {
        // TODO Save an allocation and do this lazily?
        call.cloudClient = KtorCallBoundAuthenticatedCloud(call, baseCloud)
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, CloudClient, CloudClient> {
        override val key = AttributeKey<CloudClient>("cloudClientFeature")

        override fun install(pipeline: ApplicationCallPipeline, configure: CloudClient.() -> Unit): CloudClient {
            val feature = CloudClient()

            feature.configure()
            if (!feature::baseCloud.isInitialized) {
                throw IllegalStateException("You need to set the baseCloud property in the configure block of install!")
            }

            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(this) }
            return feature
        }
    }
}

private val authenticatedCloudClient = AttributeKey<AuthenticatedCloud>("cloudClient")
var ApplicationCall.cloudClient: AuthenticatedCloud
    get() = attributes[authenticatedCloudClient]
    private set(value) = attributes.put(authenticatedCloudClient, value)

private class KtorCallBoundAuthenticatedCloud(
    private val call: ApplicationCall,
    private val delegate: AuthenticatedCloud
) : AuthenticatedCloud {
    override val parent: CloudContext
        get() = delegate.parent

    override fun BoundRequestBuilder.configureCall() {
        with (delegate) { configureCall() }

        val jobId = call.request.safeJobId
        if (jobId != null) {
            setHeader("Job-Id", UUID.randomUUID())
            setHeader("Caused-By", jobId)
        }
    }
}
