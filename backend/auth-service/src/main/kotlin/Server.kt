package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.onelogin.saml2.settings.Saml2Settings
import dk.sdu.cloud.auth.api.ServiceAgreementText
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.auth.http.*
import dk.sdu.cloud.auth.services.*
import dk.sdu.cloud.auth.services.saml.SamlRequestProcessor
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
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
        val twoFactorDao = TwoFactorAsyncDAO()
        val principalService = PrincipalService(passwordHashingService, db, serviceClient)
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

        val associatedByService = config.tokenExtension.groupBy { it.serviceName }
        val mergedExtensions = associatedByService.map { (service, lists) ->
            service to lists.flatMap { it.parsedScopes }.toSet()
        }.toMap()

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
            mergedExtensions,
            devMode = micro.developmentModeEnabled
        )

        val loginResponder = LoginResponder(tokenService, twoFactorChallengeService)

        val sessionService = SessionService(db, refreshTokenDao)
        val slaService = SLAService(config.serviceLicenseAgreement ?: ServiceAgreementText(0, ""), db, principalService)

        val providerDao = ProviderDao()
        val providerService = ProviderService(micro.developmentModeEnabled, db, providerDao)

        val registrationService = RegistrationService(db, loginResponder, serviceClient, principalService,
            usernameGenerator, micro.backgroundScope)

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
                    ktor = micro.feature(ServerFeature).ktorApplicationEngine?.application
                ),

                UserController(
                    db = db,
                    principalService = principalService,
                    tokenService = tokenService,
                    unconditionalPasswordResetWhitelist = config.unconditionalPasswordResetWhitelist,
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
