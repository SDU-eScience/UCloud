package dk.sdu.cloud.zenodo

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.kafka.forStream
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.WithKafkaStreams
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.zenodo.api.ZenodoCommandStreams
import dk.sdu.cloud.zenodo.http.ZenodoController
import dk.sdu.cloud.zenodo.processors.PublishProcessor
import dk.sdu.cloud.zenodo.services.ZenodoOAuth
import dk.sdu.cloud.zenodo.services.ZenodoRPCService
import dk.sdu.cloud.zenodo.services.hibernate.PublicationHibernateDAO
import dk.sdu.cloud.zenodo.services.hibernate.ZenodoOAuthHibernateStateStore
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.route
import io.ktor.routing.routing
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

class Server(
    private val config: Configuration,
    override val micro: Micro
) : CommonServer, WithKafkaStreams {
    override val log: Logger = logger()

    override lateinit var kStreams: KafkaStreams

    override fun start() {
        val db = micro.hibernateDatabase
        val kafka = micro.kafka
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        log.info("Configuring core services")
        val zenodoOauth = ZenodoOAuth(
            db = db,
            clientSecret = config.zenodo.clientSecret,
            clientId = config.zenodo.clientId,

            callback = "https://cloud.sdu.dk/zenodo/oauth",

            stateStore = ZenodoOAuthHibernateStateStore(),

            useSandbox = config.zenodo.useSandbox
        )

        val zenodo = ZenodoRPCService(config.zenodo.useSandbox, zenodoOauth)
        val publicationService = PublicationHibernateDAO(config.zenodo.useSandbox)
        log.info("Core services configured")

        kStreams = buildStreams { kBuilder ->
            PublishProcessor(db, zenodo, publicationService, client, micro.tokenValidation).also {
                it.init(
                    kBuilder
                )
            }
        }

        with(micro.server) {
            with(micro.feature(ServerFeature).ktorApplicationEngine!!.application) {
                routing {
                    route("zenodo") {
                        get("oauth") {
                            val state =
                                call.request.queryParameters["state"]
                                    ?: return@get call.respond(HttpStatusCode.BadRequest)
                            val code =
                                call.request.queryParameters["code"]
                                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                            val redirectTo = zenodoOauth.requestTokenWithCode(code, state) ?: "/"
                            call.respondRedirect(redirectTo)
                        }
                    }
                }
            }

            configureControllers(
                ZenodoController(
                    db,
                    publicationService,
                    zenodo,
                    kafka.producer.forStream(ZenodoCommandStreams.publishCommands),
                    client
                )
            )
        }

        startServices()
    }
}
