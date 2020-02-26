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
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import kotlin.system.exitProcess

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
    val concurrency = config.concurrency ?: 100

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

    val userA = UserAndClient(config.userA.username, authenticatedClientA)
    val userB = UserAndClient(config.userB.username, authenticatedClientB)

    val testToRun = args.indexOf("--run-test").takeIf { it != -1 }?.let { args.getOrNull(it + 1) }
    val runAllTests = testToRun == null
    fun shouldRun(testName: String): Boolean = runAllTests || testToRun == testName

    while (true) {
        try {
            Integration.log.info("Running tests")
            if (shouldRun("support")) {
                SupportTesting(userA).runTest()
            }

            if (shouldRun("avatar")) {
                AvatarTesting(userA, userB).runTest()
            }

            if (shouldRun("file-favorite")) {
                FileFavoriteTest(userA).runTest()
            }

            if (shouldRun("files")) {
                FileTesting(userA, userB).runTest()
            }

            if (shouldRun("batch-app")) {
                BatchApplication(userA).runTest()
            }

            if (shouldRun("file-activity")) {
                FileActivityTest(userA).runTest()
            }

            if (shouldRun("concurrent-upload")) {
                ConcurrentFileUploadsTest(userA, concurrency).runTest()
            }

            if (shouldRun("app-search")) {
                AppSearchTesting(userA).runTest()
            }

        } catch (ex: Throwable) {
            Integration.log.warn(ex.stackTraceToString())
            if (!runAllTests) {
                exitProcess(1)
            }
        } finally {
            if (runAllTests) {
                delay(1000L * 60 * 15)
            }
        }

        if (!runAllTests) {
            exitProcess(0)
        }
    }
}
