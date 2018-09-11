package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.PasswordController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.http.UserController
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.client.AuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.apache.xml.security.utils.Base64
import org.slf4j.Logger
import java.security.SecureRandom

class Server(
    private val db: HibernateSessionFactory,
    private val cloud: AuthenticatedCloud,
    private val jwtAlg: Algorithm,
    private val config: AuthConfiguration,
    private val authSettings: Saml2Settings,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val instance: ServiceInstance,
    private val developmentMode: Boolean
) : CommonServer {
    override val log: Logger = logger()

    override val kStreams: KafkaStreams? = null
    override lateinit var httpServer: ApplicationEngine

    override fun start() {
        log.info("Creating core services...")
        val userDao = UserHibernateDAO()
        val refreshTokenDao = RefreshTokenHibernateDAO()
        val ottDao = OneTimeTokenHibernateDAO()
        val userCreationService = UserCreationService(
            db,
            userDao,
            kafka.producer.forStream(AuthStreams.UserUpdateStream)
        )

        val associatedByService = config.tokenExtension.groupBy { it.serviceName }
        val mergedExtensions = associatedByService.map { (service, lists) ->
            service to lists.flatMap { it.parsedScopes }.toSet()
        }.toMap()


        val tokenService = TokenService(
            db,
            userDao,
            refreshTokenDao,
            jwtAlg,
            userCreationService,
            mergedExtensions
        )

        log.info("Core services constructed!")

        if (developmentMode) {
            log.info("In development mode. Checking if we need to create a dummy account.")
            db.withTransaction {
                val existingDevAdmin = userDao.findByIdOrNull(it, "admin@dev")
                if (existingDevAdmin == null) {
                    log.info("Creating a dummy admin")
                    val random = SecureRandom()
                    val passwordBytes = ByteArray(64)
                    random.nextBytes(passwordBytes)
                    val password = Base64.encode(passwordBytes)

                    val user = PersonUtils.createUserByPassword(
                        "Admin",
                        "Dev",
                        "admin@dev",
                        Role.ADMIN,
                        password
                    )

                    userCreationService.blockingCreateUser(user)
                    val token = tokenService.createAndRegisterTokenFor(user, 1000 * 60 * 60 * 24 * 365L)

                    log.info("Username: admin@dev")
                    log.info("accessToken = ${token.accessToken}")
                    log.info("refreshToken = ${token.refreshToken}")
                    log.info("Access token expires in one year.")
                }
            }
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
                config.enableWayf,
                config.trustedOrigins.toSet()
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
