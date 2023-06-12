package dk.sdu.cloud.micro

import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.ScriptManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.slf4j.Logger

class PrometheusFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        ctx.requireFeature(KtorServerProviderFeature)

        ctx.serverProvider(Prometheus.metricsPort) {
            routing {
                val handler: suspend PipelineContext<Unit, ApplicationCall>.(Unit) -> Unit = {
                    val responder = Prometheus.respondToPrometheusQuery(call.request.header(HttpHeaders.Accept))
                    call.respondTextWriter(ContentType.parse(responder.contentType), writer = responder.generator)
                }

                get(Prometheus.metricsEndpoint, handler)
                post(Prometheus.metricsEndpoint, handler)
            }
        }.start(wait = false)
    }

    companion object : Loggable, MicroFeatureFactory<PrometheusFeature, Unit> {
        override val key = MicroAttributeKey<PrometheusFeature>("prometheus-feature")
        override fun create(config: Unit): PrometheusFeature = PrometheusFeature()
        override val log: Logger = logger()
    }
}
