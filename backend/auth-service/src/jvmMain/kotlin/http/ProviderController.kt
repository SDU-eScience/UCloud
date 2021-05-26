package dk.sdu.cloud.auth.http

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.auth.api.AuthProvidersGenerateKeyPairResponse
import dk.sdu.cloud.auth.api.AuthProvidersRetrievePublicKeyResponse
import dk.sdu.cloud.auth.services.ProviderService
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.calls.server.audit
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

class ProviderController(private val service: ProviderService) : Controller {
    override fun configure(rpcServer: RpcServer) = with(rpcServer) {
        implement(AuthProviders.claim) {
            ok(service.claimRegistration(request))
        }

        implement(AuthProviders.refresh) {
            audit(BulkResponse<FindByStringId>(emptyList()))
            val refreshed = service.refreshToken(request)
            audit(BulkResponse(refreshed.responses.map { FindByStringId(it.first) }))
            ok(BulkResponse(refreshed.responses.map { it.second }))
        }

        implement(AuthProviders.refreshAsOrchestrator) {
            ok(service.refreshTokenAsOrchestrator(actorAndProject, request))
        }

        implement(AuthProviders.register) {
            ok(service.registerProvider(request))
        }

        implement(AuthProviders.renew) {
            ok(service.renewToken(request))
        }

        implement(AuthProviders.retrievePublicKey) {
            ok(AuthProvidersRetrievePublicKeyResponse(service.retrievePublicKey(request.id)))
        }

        implement(AuthProviders.generateKeyPair) {
            val keys = service.generateKeys()
            ok(AuthProvidersGenerateKeyPairResponse(
                keys.publicKey,
                keys.privateKey
            ))
        }
        return@with
    }
}