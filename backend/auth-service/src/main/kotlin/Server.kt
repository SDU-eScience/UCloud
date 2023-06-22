package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.auth.api.ServiceAgreementText
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.auth.http.*
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.*
import io.ktor.server.routing.*

class Server(
    private val jwtAlg: Algorithm,
    private val config: AuthConfiguration,
    private val authSettings: Saml2Settings,
    override val micro: Micro,
) : CommonServer {
    override val log: Logger = logger()

    override fun start() {
        val db = AsyncDBSessionFactory(micro)
        @Suppress("UNCHECKED_CAST") val tokenValidation = micro.tokenValidation as TokenValidation<DecodedJWT>
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

        val passwordHashingService = PasswordHashingService()
        val principalService = PrincipalService(passwordHashingService, db, serviceClient)
        val twoFactorDao = TwoFactorAsyncDAO(principalService)
        val refreshTokenDao = RefreshTokenAsyncDAO()
        val usernameGenerator = UniqueUsernameService(db, principalService)
        val ottDao = OneTimeTokenAsyncDAO()

        val totpService = WSTOTPService()
        val qrService = ZXingQRService()

        val twoFactorChallengeService = TwoFactorChallengeService(
            db,
            twoFactorDao,
            principalService,
            totpService,
            qrService
        )

        val ownHost = when {
            config.wayfReturn != null -> {
                val url = Url(config.wayfReturn)
                HostInfo(url.host, url.protocol.name, url.port)
            }

            micro.developmentModeEnabled -> {
                HostInfo("ucloud.localhost.direct", "https")
            }

            else -> {
                HostInfo("cloud.sdu.dk", "https")
            }
        }

        val idpService = IdpService(db, micro, ownHost)
        val tokenService = TokenService(
            db,
            principalService,
            refreshTokenDao,
            JWTFactory(
                jwtAlg,
                config.serviceLicenseAgreement,
                disable2faCheck = micro.developmentModeEnabled || config.disable2faCheck
            ),
            tokenValidation,
            devMode = micro.developmentModeEnabled,
            usernameService = usernameGenerator,
            idpService = idpService,
        )

        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)
        val registrationService = RegistrationService(db, loginResponder, serviceClient, principalService,
            usernameGenerator, micro.backgroundScope, idpService)

        // TODO(Dan): One day we will need to sort out this mess
        idpService.registrationServiceCyclicHack = registrationService
        idpService.loginResponderCyclicHack = loginResponder
        idpService.principalServiceCyclicHack = principalService

        val sessionService = SessionService(db, refreshTokenDao)
        val slaService = SLAService(config.serviceLicenseAgreement ?: ServiceAgreementText(0, ""), db, principalService)

        val providerDao = ProviderDao()
        val providerService = ProviderService(micro.developmentModeEnabled, db, providerDao)


        val scriptManager = micro.feature(ScriptManager)
        scriptManager.register(
            Script(
                ScriptMetadata(
                    "token-scan",
                    "Authentication: Token Scan",
                    WhenToStart.Daily(0, 0)
                ),
                script = {
                    db.withTransaction { session ->
                        refreshTokenDao.deleteExpired(session)
                    }
                }
            )
        )

        val serverFeature = micro.feature(ServerFeature)
        with(serverFeature.ktorApplicationEngine!!.application) {
            val loginService =
                LoginService(db, passwordHashingService, principalService, LoginAttemptAsyncDao(), loginResponder)
            val passwordController = PasswordController(loginService)

            if (config.enableWayf) {
                val samlController = SAMLController(
                    idpService,
                    authSettings,
                    { settings, call, params -> SamlRequestProcessor(settings, call, params) },
                    tokenService,
                    loginResponder,
                    registrationService,
                )

                routing {
                    route("/auth/saml") {
                        samlController.configure(this)
                    }
                }
            }

            if (config.enablePasswords) configureControllers(passwordController)

            configureControllers(
                CoreAuthController(
                    db = db,
                    ottDao = ottDao,
                    tokenService = tokenService,
                    tokenValidation = tokenValidation,
                    trustedOrigins = config.trustedOrigins.toSet(),
                    ktor = micro.feature(ServerFeature).ktorApplicationEngine?.application,
                    idpService = idpService
                ),

                UserController(
                    db = db,
                    principalService = principalService,
                    tokenService = tokenService,
                    unconditionalPasswordResetWhitelist = config.unconditionalPasswordResetWhitelist,
                    passwordHashingService = passwordHashingService,
                    devMode = micro.developmentModeEnabled,
                ),

                TwoFactorAuthController(twoFactorChallengeService, loginResponder),
                SessionsController(sessionService),
                SLAController(slaService),
                ProviderController(providerService),
                RegistrationController(registrationService)
            )
        }

        startServices()
    }
}
