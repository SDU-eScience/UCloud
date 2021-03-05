package dk.sdu.cloud.app.aau 

import dk.sdu.cloud.app.aau.rpc.ComputeController
import dk.sdu.cloud.app.aau.services.ResourceCache
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.project.api.CreateProjectRequest
import dk.sdu.cloud.project.api.Projects
import dk.sdu.cloud.provider.api.ProviderSpecification
import dk.sdu.cloud.provider.api.Providers
import dk.sdu.cloud.provider.api.ProvidersRetrieveRequest
import dk.sdu.cloud.service.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

data class ClientHolder(var client: AuthenticatedClient)

class Server(override val micro: Micro, private val configuration: Configuration) : CommonServer {
    override val log = logger()

    private var requireTokenInit = false
    private lateinit var client: ClientHolder

    override fun start() {
        val (refreshToken, validation) =
            if (configuration.providerRefreshToken == null || configuration.ucloudCertificate == null) {
                if (!micro.developmentModeEnabled) {
                    throw IllegalStateException("Missing configuration at app.kubernetes.providerRefreshToken")
                }
                requireTokenInit = true
                Pair("REPLACED_LATER", InternalTokenValidationJWT.withSharedSecret(UUID.randomUUID().toString()))
            } else {
                Pair(
                    configuration.providerRefreshToken,
                    InternalTokenValidationJWT.withPublicCertificate(configuration.ucloudCertificate)
                )
            }

        val authenticator = RefreshingJWTAuthenticator(micro.client, JwtRefresher.Provider(refreshToken))
        client = ClientHolder(authenticator.authenticateClient(OutgoingHttpCall))
        @Suppress("UNCHECKED_CAST")
        micro.providerTokenValidation = validation as TokenValidation<Any>

        with(micro.server) {
             configureControllers(
                 ComputeController(client, ResourceCache(client), micro.developmentModeEnabled),
             )
        }
        
        startServices()
    }

    override fun onKtorReady() {
        if (requireTokenInit) {
            log.warn("Initializing a provider for UCloud in development mode")
            runBlocking {
                val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
                val project = Projects.create.call(
                    CreateProjectRequest("UCloudProviderForAauTemp"),
                    serviceClient
                ).orThrow()

                Providers.create.call(
                    bulkRequestOf(
                        ProviderSpecification(
                            "aau",
                            "localhost",
                            false,
                            8080
                        )
                    ),
                    serviceClient.withProject(project.id)
                ).orRethrowAs {
                    throw IllegalStateException("Could not register a provider for development mode!")
                }

                val retrievedResponse = Providers.retrieve.call(
                    ProvidersRetrieveRequest("aau"),
                    serviceClient.withProject(project.id)
                ).orThrow()

                if (micro.developmentModeEnabled) {
                    val defaultConfigDir = File(System.getProperty("user.home"), "sducloud")
                    val configFile = File(defaultConfigDir, "ucloud-aau-compute-config.yml")
                    log.warn("Provider configuration is stored at: ${configFile.absolutePath}")
                    configFile.writeText(
                        //language=yaml
                        """
                          ---
                          app:
                            aau:
                              providerRefreshToken: ${retrievedResponse.refreshToken}
                              ucloudCertificate: ${retrievedResponse.publicKey}
                        """.trimIndent()
                    )
                }

                @Suppress("UNCHECKED_CAST")
                micro.providerTokenValidation = InternalTokenValidationJWT
                    .withPublicCertificate(retrievedResponse.publicKey) as TokenValidation<Any>

                client.client = RefreshingJWTAuthenticator(
                    micro.client,
                    JwtRefresher.Provider(retrievedResponse.refreshToken)
                ).authenticateClient(OutgoingHttpCall)
            }
        }
    }
}
