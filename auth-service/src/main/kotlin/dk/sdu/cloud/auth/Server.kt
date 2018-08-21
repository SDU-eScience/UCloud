package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.PasswordController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.http.UserController
import dk.sdu.cloud.auth.processors.OneTimeTokenProcessor
import dk.sdu.cloud.auth.processors.RefreshTokenProcessor
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger

class Server(
    private val db: HibernateSessionFactory,
    private val cloud: AuthenticatedCloud,
    private val jwtAlg: Algorithm,
    private val config: AuthConfiguration,
    private val authSettings: Saml2Settings,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val instance: ServiceInstance
) : CommonServer {
    override val log: Logger = logger()

    override lateinit var kStreams: KafkaStreams
    override lateinit var httpServer: ApplicationEngine

    override fun start() {
        log.info("Creating core services...")
        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()
        val ottDao = OneTimeTokenHibernateDAO()

        val tokenService = TokenService(
            db,
            userDao,
            refreshTokenDao,
            jwtAlg,
            kafka.producer.forStream(AuthStreams.UserUpdateStream),
            kafka.producer.forStream(AuthStreams.RefreshTokenStream),
            kafka.producer.forStream(AuthStreams.OneTimeTokenStream)
        )
        val userCreationService = UserCreationService(
            db,
            userDao,
            kafka.producer.forStream(AuthStreams.UserUpdateStream)
        )
        log.info("Core services constructed!")

        kStreams = buildStreams { kBuilder ->
            RefreshTokenProcessor(
                db,
                refreshTokenDao,
                kBuilder.stream(AuthStreams.RefreshTokenStream)
            ).also { it.init() }

            OneTimeTokenProcessor(kBuilder.stream(AuthStreams.OneTimeTokenStream)).also { it.init() }
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")

            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)

            log.info("Creating HTTP controllers")
            val coreController = CoreAuthController(
                db,
                ottDao,
                tokenService,
                config.enablePasswords,
                config.enableWayf
            )

            val samlController = SAMLController(
                authSettings,
                { settings, call, params -> SamlRequestProcessor(settings, call, params) },
                tokenService
            )

            val passwordController = PasswordController(db, userDao, tokenService)
            log.info("HTTP controllers configured!")

            routing {
                coreController.configure(this)
                if (config.enableWayf) samlController.configure(this)
                if (config.enablePasswords) passwordController.configure(this)

                configureControllers(
                    UserController(
                        db,
                        userDao,
                        userCreationService,
                        tokenService
                    )
                )
            }

            log.info("HTTP server successfully configured!")
        }

        startServices()
    }
}
