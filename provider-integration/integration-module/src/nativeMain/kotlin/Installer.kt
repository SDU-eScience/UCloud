package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.ProductCategoryId
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.controllers.EnvoyConfigurationService
import dk.sdu.cloud.controllers.UCLOUD_IM_PORT
import dk.sdu.cloud.http.H2OServer
import dk.sdu.cloud.http.OutgoingCallResponse
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.LogLevel
import dk.sdu.cloud.service.LogManager
import dk.sdu.cloud.service.currentLogLevel
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.ProcessStreams
import dk.sdu.cloud.utils.replaceThisProcess
import dk.sdu.cloud.utils.writeText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import platform.posix.sleep
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.system.exitProcess

fun runInstaller(
    core: IMConfiguration.Core,
    server: IMConfiguration.Server,
    ownExecutable: String,
) {
    println(
        "Welcome to the UCloud/IM installer. This installation process will register an integration module with " +
            "a UCloud/Core instance."
    )

    if (server.ucloud.host == "backend") {
        println("This setup has been detected as a local development setup, using the in-tree compose file.")
    } else {
        println("Not yet implemented for non-compose setups")
        exitProcess(1)
    }

    val rpcClient = RpcClient()
    OutgoingHttpRequestInterceptor().also {
        it.install(
            rpcClient, FixedOutgoingHostResolver(
                HostInfo(
                    server.ucloud.host,
                    server.ucloud.scheme,
                    server.ucloud.port
                )
            )
        )
    }

    val authenticatedClient = AuthenticatedClient(rpcClient, OutgoingHttpCall) {}

    val providerId = "development"
    val port = 8889
    val result = try {
        Providers.requestApproval.callBlocking(
            ProvidersRequestApprovalRequest.Information(
                ProviderSpecification(
                    providerId,
                    "integration-module",
                    false,
                    port
                )
            ),
            authenticatedClient
        ).orThrow()
    } catch (ex: Throwable) {
        println("UCloud/IM could not connect to UCloud/Core!")

        println("  - If you are running a local setup, please make sure UCloud is running.")
        println("  - If you are attempting to register a production system, make sure that UCloud/IM can connect to " +
            "UCloud at ${server.ucloud.scheme}://${server.ucloud.host}:${server.ucloud.port}")
        exitProcess(1)
    }

    val token = when (result) {
        is ProvidersRequestApprovalResponse.RequiresSignature -> result.token
        is ProvidersRequestApprovalResponse.AwaitingAdministratorApproval -> result.token
    }

    println()
    println()

    println(
        "UCloud/IM has registered with UCloud/Core. Please finish the configuration by approving the connection" +
            " here:"
    )
    println("http://localhost:9000/app/admin/providers/register?token=$token")

    println()
    println()

    currentLogLevel = LogLevel.INFO
    val envoy = EnvoyConfigurationService(ENVOY_CONFIG_PATH)
    val h2oServer = H2OServer(UCLOUD_IM_PORT, showWelcomeMessage = false)

    val ip = IntegrationProvider(providerId)
    h2oServer.implement(ip.welcome) {
        currentLogLevel = LogLevel.INFO

        NativeFile.open(
            "${IMConfiguration.CONFIG_PATH}/core.json",
            readOnly = false,
            truncateIfNeeded = true,
            mode = "644".toInt(8)
        ).writeText(
            defaultMapper.encodeToString(
                core.copy(
                    providerId = providerId,
                    certificateFile = "${IMConfiguration.CONFIG_PATH}/certificate.txt"
                )
            )
        )

        NativeFile.open(
            "${IMConfiguration.CONFIG_PATH}/certificate.txt",
            readOnly = false,
            truncateIfNeeded = true,
            mode = "644".toInt(8)
        ).writeText(request.createdProvider.publicKey)

        NativeFile.open(
            "${IMConfiguration.CONFIG_PATH}/server.json",
            readOnly = false,
            truncateIfNeeded = true,
            mode = "600".toInt(8)
        ).writeText(
            defaultMapper.encodeToString(
                server.copy(
                    refreshToken = request.createdProvider.refreshToken
                )
            )
        )

        data class ShutdownArgs(
            val ownExecutable: String,
            val refreshToken: String,
            val server: IMConfiguration.Server,
            val providerId: String
        )
        Worker.start(name = "Shutting down")
            .execute(TransferMode.SAFE, {
                ShutdownArgs(ownExecutable, request.createdProvider.refreshToken, server, providerId)
            }) {
                runShutdownWorker(it.ownExecutable, it.refreshToken, it.server, it.providerId)
            }

        OutgoingCallResponse.Ok(IntegrationProviderWelcomeResponse)
    }

    envoy.start(port)
    h2oServer.start()
}

private fun runShutdownWorker(
    ownExecutable: String,
    refreshToken: String,
    server: IMConfiguration.Server,
    providerId: String
) {
    sleep(1)
    val rpcClient = RpcClient()
    OutgoingHttpRequestInterceptor().also {
        it.install(
            rpcClient, FixedOutgoingHostResolver(
                HostInfo(
                    server.ucloud.host,
                    server.ucloud.scheme,
                    server.ucloud.port
                )
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    Products.createProduct.callBlocking(
        Product.Compute(
            "im1",
            0L,
            ProductCategoryId("im1", providerId),
            "Example product",
            cpu = 1,
            memoryInGigs = 1,
            gpu = 0
        ),
        RefreshingJWTAuthenticator(
            rpcClient,
            JwtRefresher.Provider(refreshToken),
            becomesInvalidSoon = { true }
        ).authenticateClient(OutgoingHttpCall)
    ).orThrow()
    println("UCloud/IM has been configured successfully. UCloud/IM will now restart...")
    replaceThisProcess(listOf(ownExecutable, "server"), ProcessStreams())
}
