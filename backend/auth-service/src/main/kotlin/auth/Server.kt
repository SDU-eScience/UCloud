package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.Role
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.ServiceAgreementText
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.auth.http.CoreAuthController
import dk.sdu.cloud.auth.http.LoginResponder
import dk.sdu.cloud.auth.http.PasswordController
import dk.sdu.cloud.auth.http.SAMLController
import dk.sdu.cloud.auth.http.SLAController
import dk.sdu.cloud.auth.http.SessionsController
import dk.sdu.cloud.auth.http.TwoFactorAuthController
import dk.sdu.cloud.auth.http.UserController
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.startServices
import io.ktor.routing.route
import io.ktor.routing.routing
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File
import java.security.SecureRandom
import java.util.*
import kotlin.system.exitProcess

private const val ONE_YEAR_IN_MILLS = 1000 * 60 * 60 * 24 * 365L
private const val PASSWORD_BYTES = 64

class Server(
    private val jwtAlg: Algorithm,
    private val config: AuthConfiguration,
    private val authSettings: Saml2Settings,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    private lateinit var userIterator: UserIterationService

    override fun start() {
        log.info("Creating core services...")
        val db = micro.hibernateDatabase
        val tokenValidation = micro.tokenValidation as TokenValidationJWT
        val streams = micro.eventStreamService

        val passwordHashingService = PasswordHashingService()
        val twoFactorDao = TwoFactorHibernateDAO()
        val userDao = UserHibernateDAO(passwordHashingService, twoFactorDao)
        val refreshTokenDao = RefreshTokenHibernateDAO()
        val usernameGenerator = UniqueUsernameService(db, userDao)
        val personService = PersonService(passwordHashingService, usernameGenerator)
        val ottDao = OneTimeTokenHibernateDAO()
        val userCreationService = UserCreationService(
            db,
            userDao,
            streams.createProducer(AuthStreams.UserUpdateStream)
        )

        val totpService = WSTOTPService()
        val qrService = ZXingQRService()
        val cursorStateDao = CursorStateHibernateDao()
        userIterator = UserIterationService(
            micro.serviceInstance.ipAddress ?: "localhost",
            micro.serviceInstance.port,
            db,
            cursorStateDao,
            micro.client,
            micro.authenticator
        )

        userIterator.start()


        val twoFactorChallengeService = TwoFactorChallengeService(
            db,
            twoFactorDao,
            userDao,
            totpService,
            qrService
        )

        val associatedByService = config.tokenExtension.groupBy { it.serviceName }
        val mergedExtensions = associatedByService.map { (service, lists) ->
            service to lists.flatMap { it.parsedScopes }.toSet()
        }.toMap()

        // TODO This service is accepting way too many dependencies.
        val tokenService = TokenService(
            db,
            personService,
            userDao,
            refreshTokenDao,
            JWTFactory(jwtAlg, config.serviceLicenseAgreement),
            userCreationService,
            tokenValidation,
            mergedExtensions,
            devMode = micro.developmentModeEnabled
        )

        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)

        val sessionService = SessionService(db, refreshTokenDao)
        val slaService = SLAService(config.serviceLicenseAgreement ?: ServiceAgreementText(0, ""), db, userDao)

        log.info("Core services constructed!")

        if (micro.commandLineArguments.contains("--tokenScan")) {
            log.info("Scanning for expired refresh tokens.")
            runBlocking {
                db.withTransaction { session ->
                    refreshTokenDao.deleteExpired(session)
                }
            }
            exitProcess(0)
        }

        if (micro.developmentModeEnabled) {
            runBlocking {
                log.info("In development mode. Checking if we need to create a dummy account.")
                val existingDevAdmin = db.withTransaction { userDao.findByIdOrNull(it, "admin@dev") }
                val adminUser = "admin@dev"
                if (existingDevAdmin == null) {
                    createTestAccount(personService, userCreationService, tokenService, adminUser, Role.ADMIN)
                    createTestAccount(personService, userCreationService, tokenService, "user@dev", Role.USER)
                } else {
                    val idx = micro.commandLineArguments.indexOf("--save-config")
                    if (idx != -1) {
                        val tokens = db.withTransaction { session ->
                            refreshTokenDao.findTokenForUser(session, adminUser)
                        }
                        val refreshToken = tokens?.token
                        val csrfToken = tokens?.csrf
                        val configLocation = micro.commandLineArguments.getOrNull(idx + 1)
                        if (configLocation != null) {
                            File(configLocation).writeText(
                                """
                            ---
                            refreshToken: "$refreshToken"
                            devCsrfToken: $csrfToken
                            """.trimIndent()
                            )
                        }
                    }
                }
            }
        }

        val serverFeature = micro.feature(ServerFeature)
        with(serverFeature.ktorApplicationEngine!!.application) {
            log.info("Configuring HTTP server")

            log.info("Creating HTTP controllers")

            val samlController = SAMLController(
                authSettings,
                { settings, call, params -> SamlRequestProcessor(settings, call, params) },
                tokenService,
                loginResponder
            )

            val loginService =
                LoginService(db, passwordHashingService, userDao, LoginAttemptHibernateDao(), loginResponder)
            val passwordController = PasswordController(loginService)
            log.info("HTTP controllers configured!")

            if (config.enableWayf) {
                routing {
                    route("/auth/saml") {
                        samlController.configure(this)
                    }
                }
            }

            with(micro.server) {
                if (config.enablePasswords) configureControllers(passwordController)

                configureControllers(
                    // TODO Too many dependencies
                    CoreAuthController(
                        db = db,
                        ottDao = ottDao,
                        tokenService = tokenService,
                        tokenValidation = tokenValidation,
                        trustedOrigins = config.trustedOrigins.toSet(),
                        ktor = micro.feature(ServerFeature).ktorApplicationEngine?.application
                    ),

                    // TODO Too many dependencies
                    UserController(
                        db = db,
                        personService = personService,
                        userDAO = userDao,
                        userCreationService = userCreationService,
                        tokenService = tokenService,
                        userIterationService = userIterator,
                        unconditionalPasswordResetWhitelist = config.unconditionalPasswordResetWhitelist,
                        developmentMode = micro.developmentModeEnabled
                    ),

                    TwoFactorAuthController(twoFactorChallengeService, loginResponder),

                    SessionsController(sessionService),

                    SLAController(slaService)
                )
            }

            log.info("HTTP server successfully configured!")
        }

        startServices()
    }

    private suspend fun createTestAccount(
        personService: PersonService,
        userCreationService: UserCreationService<HibernateSession>,
        tokenService: TokenService<HibernateSession>,

        username: String,
        role: Role
    ) {
        log.info("Creating a dummy admin")
        val random = SecureRandom()
        val passwordBytes = ByteArray(PASSWORD_BYTES)
        random.nextBytes(passwordBytes)
        val password = Base64.getEncoder().encodeToString(passwordBytes)

        val user = personService.createUserByPassword(
            "Admin",
            "Dev",
            username,
            role,
            password,
            "escience@sdu.dk"
        )

        userCreationService.blockingCreateUser(user)
        val token = tokenService.createAndRegisterTokenFor(
            user, AccessTokenContents(
                user,
                listOf(SecurityScope.ALL_WRITE),
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + ONE_YEAR_IN_MILLS
            ),
            userAgent = null,
            ip = null
        )

        log.info("Username: $username")
        log.info("accessToken = ${token.accessToken}")
        log.info("refreshToken = ${token.refreshToken}")
        log.info("csrfToken = ${token.csrfToken}")
        log.info("Access token expires in one year.")
        log.info("Password is: '$password'")
        log.info("---------------")

        if (role == Role.ADMIN) {
            val idx = micro.commandLineArguments.indexOf("--save-config")
            if (idx != -1) {
                val configLocation = micro.commandLineArguments.getOrNull(idx + 1) ?: return
                File(configLocation).writeText(
                    """
                        ---
                        refreshToken: "${token.refreshToken}"
                        devCsrfToken: ${token.csrfToken}
                        devPassword: $password
                    """.trimIndent()
                )
            }
        }
    }

    override fun stop() {
        super.stop()
        userIterator.stop()
    }
}
