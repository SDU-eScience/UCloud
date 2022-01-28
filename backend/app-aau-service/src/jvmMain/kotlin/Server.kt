package dk.sdu.cloud.app.aau 

import dk.sdu.cloud.app.aau.rpc.ComputeController
import dk.sdu.cloud.app.aau.services.ResourceCache
import dk.sdu.cloud.auth.api.JwtRefresher
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.*
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import java.util.*

data class ClientHolder(var client: AuthenticatedClient)

class Server(override val micro: Micro, private val configuration: Configuration) : CommonServer {
    override val log = logger()

    private lateinit var client: ClientHolder

    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val (refreshToken, validation) =
            if (configuration.providerRefreshToken == null || configuration.ucloudCertificate == null) {
                if (!micro.developmentModeEnabled) {
                    throw IllegalStateException("Missing configuration at app.kubernetes.providerRefreshToken")
                }
                Pair("REPLACED_LATER", InternalTokenValidationJWT.withSharedSecret(UUID.randomUUID().toString()))
            } else {
                Pair(
                    configuration.providerRefreshToken,
                    InternalTokenValidationJWT.withPublicCertificate(configuration.ucloudCertificate)
                )
            }

        val authenticator = RefreshingJWTAuthenticator(micro.client, JwtRefresher.Provider(refreshToken, OutgoingHttpCall))
        client = ClientHolder(authenticator.authenticateClient(OutgoingHttpCall))
        @Suppress("UNCHECKED_CAST")
        micro.providerTokenValidation = validation as TokenValidation<Any>

        with(micro.server) {
             configureControllers(
                 ComputeController(
                     client,
                     ResourceCache(client),
                     serviceClient,
                     micro.developmentModeEnabled,
                     micro.tokenValidation,
                 ),
             )
        }
        
        startServices()
    }
}
