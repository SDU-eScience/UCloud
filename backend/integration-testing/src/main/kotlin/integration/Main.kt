package dk.sdu.cloud.integration

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.integration.backend.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.stackTraceToString
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

object Integration : Loggable {
    override val log = logger()
}

suspend fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(object : ServiceDescription {
            override val name = "integration"
            override val version = "1"
        }, args)
        install(RefreshingJWTCloudFeature)
        install(HealthCheckFeature)
    }

    if (micro.runScriptHandler()) return

    val config = micro.configuration.requestChunkAt<Configuration>("integration")
    val concurrency = config.concurrency ?: 100

    val slackNotifier = SlackNotifier(config.slack.hook)

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

    val userA = UserAndClient(config.userA.username, authenticatedClientA, config.userA.refreshToken)
    val userB = UserAndClient(config.userB.username, authenticatedClientB, config.userB.refreshToken)

    val testToRun = args.indexOf("--run-test").takeIf { it != -1 }?.let { args.getOrNull(it + 1) }
    val runAllTests = testToRun == null
    fun shouldRun(testName: String): Boolean = runAllTests || testToRun == testName

    val hostResolver = micro.feature(ClientFeature).hostResolver

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

            if (shouldRun("shares")) {
            	ShareTesting(
                	userA,
                    userB
            	).runTest()
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

            if (shouldRun("concurrent-archive")) {
                ConcurrentArchiveTest(userA, concurrency).runTest()
            }

            if (shouldRun("concurrent-app")) {
                ConcurrentAppTest(userA, 25, hostResolver).runTest()
            }
        } catch (ex: Throwable) {
            val stackTrace = ex.stackTraceToString()
            Integration.log.warn(stackTrace)
            slackNotifier.onAlert(Alert(stackTrace))
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
