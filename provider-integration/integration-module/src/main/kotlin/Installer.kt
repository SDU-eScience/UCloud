package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.*
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.server.IngoingHttpInterceptor
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.config.*
import dk.sdu.cloud.controllers.EnvoyConfigurationService
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.utils.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlin.system.exitProcess

fun runInstaller(
    ownExecutable: String,
) {
    val isInComposeDev: Boolean = run {
        val hasBackend = verifyHost(Host("backend", "http", 8080)) is VerifyResult.Ok<*>
        val hasFrontend = verifyHost(Host("frontend", "http", 9000)) is VerifyResult.Ok<*>

        hasBackend && hasFrontend
    }

    if (isInComposeDev) {
        sendTerminalMessage {
            bold { blue { line("Welcome to the UCloud/IM installer.") } }
            code { inline("./launcher") }
            line(" usage detected!")
            line()
            line("This installation process will register the integration module with the UCloud/Core instance.")
            line()
        }

        val rpcClient = RpcClient()
        OutgoingHttpRequestInterceptor().also {
            it.install(rpcClient, FixedOutgoingHostResolver(HostInfo("backend", "http", 8080)))
        }

        val authenticatedClient = AuthenticatedClient(rpcClient, OutgoingHttpCall) {}

        val providerId = System.getenv("UCLOUD_PROVIDER_ID") ?: "development"
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
            val path = "/tmp/ucloud-im-error.log"
            sendTerminalMessage {
                bold { red { line("UCloud/IM could not connect to UCloud/Core!") } }
                line("Please make sure that the backend is running (see output of ./launcher for more information)")
                line()
                line("Error log written to: $path")
            }
            
            runCatching {
                NativeFile.open(path, readOnly = false).writeText(ex.stackTraceToString())
            }

            exitProcess(1)
        }

        val token = when (result) {
            is ProvidersRequestApprovalResponse.RequiresSignature -> result.token
            is ProvidersRequestApprovalResponse.AwaitingAdministratorApproval -> result.token
        }

        sendTerminalMessage {
            bold { green { line("UCloud/IM has registered with UCloud/Core.") } }
            line()
            line("Please finish the configuration by approving the connection here:")
            code { line("http://localhost:9000/app/admin/providers/register?token=$token") }
            line()
            bold { blue { line("Awaiting response from UCloud/Core. Please keep UCloud/IM running!") } }
        }

        val envoy = EnvoyConfigurationService(ENVOY_CONFIG_PATH)
        val server = RpcServer()
        val engine = embeddedServer(CIO, port = UCLOUD_IM_PORT) {}
        server.attachRequestInterceptor(IngoingHttpInterceptor(engine, server))

        val ip = IntegrationProvider(providerId)
        server.implement(ip.welcome) {
            NativeFile.open(
                "/etc/ucloud/core.yaml",
                readOnly = false,
                truncateIfNeeded = true,
                mode = "644".toInt(8)
            ).writeText(
                """
                    providerId: $providerId

                    hosts:
                        self:
                            host: integration-module
                            scheme: http
                            port: 8889

                        ucloud:
                            host: backend
                            scheme: http
                            port: 8080

                """.trimIndent()
            )

            NativeFile.open(
                "/etc/ucloud/ucloud_crt.pem",
                readOnly = false,
                truncateIfNeeded = true,
                mode = "644".toInt(8)
            ).writeText(request.createdProvider.publicKey)

            NativeFile.open(
                "/etc/ucloud/server.yaml",
                readOnly = false,
                truncateIfNeeded = true,
                mode = "600".toInt(8)
            ).writeText(
                """
                    refreshToken: ${request.createdProvider.refreshToken}
                """.trimIndent()
            )

            NativeFile.open(
                "/etc/ucloud/plugins.yaml",
                readOnly = false,
                truncateIfNeeded = true,
                mode = "660".toInt(8)
            ).writeText(
                """
                    connection:
                        type: UCloud

                        redirectTo: http://localhost:9000/app

                        extensions:
                            onConnectionComplete: /opt/ucloud/example-extensions/ucloud-connection

                    projects:
                        type: Simple
                        
                        unixGroupNamespace: 1000
                        extensions:
                            all: /opt/ucloud/example-extensions/project-extension

                    jobs:
                        default:
                            type: Slurm
                            matches: im-cpu
                            partition: normal
                            mountpoint: /data
                            useFakeMemoryAllocations: true

                    files:
                        default:
                            type: Posix
                            matches: im-storage

                    fileCollections:
                        default:
                            type: Posix
                            matches: im-storage
                            
                            simpleHomeMapper:
                            - title: Home
                              prefix: /home
                """.trimIndent()
            )

            NativeFile.open(
                "/etc/ucloud/products.yaml",
                readOnly = false,
                truncateIfNeeded = true,
                mode = "660".toInt(8)
            ).writeText(
                """
                    compute:
                        im-cpu:
                        - im-cpu-1

                    storage:
                        im-storage:
                        - im-storage
                """.trimIndent()
            )

            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    runShutdownWorker(ownExecutable, request.createdProvider.refreshToken, providerId)
                }
            })

            ok(IntegrationProviderWelcomeResponse)
        }

        envoy.start(port)
        server.start()
        engine.start(wait = false)
    } else {
        sendTerminalMessage {
            bold { red { line("No configuration detected!") } }
            line()
            line("Development setup not detected - Manual configuration is required!")
            line("See documentation for more details.")
        }
    }
}

private fun runShutdownWorker(
    ownExecutable: String,
    refreshToken: String,
    providerId: String
) {
    Thread.sleep(1)
    val rpcClient = RpcClient()
    OutgoingHttpRequestInterceptor().also {
        it.install(rpcClient, FixedOutgoingHostResolver(HostInfo("backend", "http", 8080)))
    }

    val authenticateClient = RefreshingJWTAuthenticator(
        rpcClient,
        JwtRefresher.Provider(refreshToken, OutgoingHttpCall),
        becomesInvalidSoon = { true }
    ).authenticateClient(OutgoingHttpCall)

    @Suppress("UNCHECKED_CAST")
    Products.create.callBlocking(
        bulkRequestOf(
            Product.Compute(
                name = "im-cpu-1",
                pricePerUnit = 1000L,
                category = ProductCategoryId("im-cpu", providerId),
                description = "Example product",
                cpu = 1,
                memoryInGigs = 1,
                gpu = 0,
            ),

            Product.Storage(
                name = "im-storage",
                pricePerUnit = 1L,
                category = ProductCategoryId("im-storage", providerId),
                description = "Example product",
                unitOfPrice = ProductPriceUnit.PER_UNIT,
                chargeType = ChargeType.DIFFERENTIAL_QUOTA,
            )
        ),
        authenticateClient
    ).orThrow()

    sendTerminalMessage {
        bold { green { line("UCloud/IM has been configured successfully. UCloud/IM will now restart...") } }
    }

    // TODO
    // replaceThisProcess(listOf(ownExecutable, "server"), ProcessStreams())
}

