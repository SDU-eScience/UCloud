package dk.sdu.cloud.integration

import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.integration.backend.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.share.api.ShareServiceDescription
import kotlinx.coroutines.delay

object Integration : Loggable {
    override val log = logger()
}

suspend fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(ShareServiceDescription, args)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("integration")

    val authenticatedClientA = RefreshingJWTAuthenticator(
        micro.client,
        config.userA.refreshToken,
        micro.tokenValidation as TokenValidationJWT
    ).authenticateClient(OutgoingHttpCall)

    val authenticatedClientB = RefreshingJWTAuthenticator(
        micro.client,
        config.userB.refreshToken,
        micro.tokenValidation as TokenValidationJWT
    ).authenticateClient(OutgoingHttpCall)

    while (true) {
        try {
            Integration.log.info("Running tests")

            FileTesting(
                UserAndClient(config.userA.username, authenticatedClientA),
                UserAndClient(config.userB.username, authenticatedClientB)
            ).runTest()

            FileFavoriteTest(
                UserAndClient(config.userA.username, authenticatedClientA)
            ).runTest()
            
            AvatarTesting(
                UserAndClient(config.userA.username, authenticatedClientA),
                UserAndClient(config.userB.username, authenticatedClientB)
            ).runTest()

            AppSearchTesting(
                UserAndClient(config.userA.username, authenticatedClientA)
            ).runTest()
        } catch (ex: Throwable) {
            Integration.log.warn(ex.stackTraceToString())
        } finally {
            delay(1000L * 60 * 15)
        }
    }
}
