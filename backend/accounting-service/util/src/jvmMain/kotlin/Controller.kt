package dk.sdu.cloud.accounting.util

import dk.sdu.cloud.accounting.api.Product
import dk.sdu.cloud.accounting.api.providers.ProductSupport
import dk.sdu.cloud.accounting.api.providers.ResourceApi
import dk.sdu.cloud.accounting.api.providers.ResourceControlApi
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.provider.api.*
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.actorAndProject

fun <Res : Resource<Prod, Support>, Spec : ResourceSpecification, Update : ResourceUpdate, Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>, Prod : Product, Support : ProductSupport, Comms : ProviderComms>
    ResourceService<Res, Spec, Update, Flags, Status, Prod, Support, Comms>.asController(): Controller {
    val userApi = userApi()
    val controlApi = runCatching { controlApi() }.getOrNull()
    return asController(userApi, controlApi)
}

fun <Res : Resource<Prod, Support>, Spec : ResourceSpecification, Update : ResourceUpdate, Flags : ResourceIncludeFlags,
    Status : ResourceStatus<Prod, Support>, Prod : Product, Support : ProductSupport>
    ResourceSvc<Res, Flags, Spec, Update, Prod, Support>.asController(
    userApi: ResourceApi<Res, Spec, Update, Flags, Status, Prod, Support>,
    controlApi: ResourceControlApi<Res, Spec, Update, Flags, Status, Prod, Support>? = null
): Controller {
    return object : Controller {
        override fun configure(rpcServer: RpcServer) = with(rpcServer) {
            userApi.create?.let {
                implement(it) {
                    ok(create(actorAndProject, request))
                }
            }

            implement(userApi.retrieveProducts) {
                ok(retrieveProducts(actorAndProject))
            }

            implement(userApi.browse) {
                ok(browse(actorAndProject, request))
            }

            implement(userApi.retrieve) {
                ok(retrieve(actorAndProject, request.id, request.flags))
            }

            userApi.delete?.let {
                implement(it) {
                    ok(delete(actorAndProject, request))
                }
            }

            implement(userApi.updateAcl) {
                ok(updateAcl(actorAndProject, request))
            }

            implement(userApi.init) {
                ok(init(actorAndProject))
            }

            if (controlApi != null) {
                implement(controlApi.browse) {
                    ok(browse(actorAndProject, request, useProject = false))
                }

                implement(controlApi.retrieve) {
                    ok(retrieve(actorAndProject, request.id, request.flags, asProvider = true))
                }

                implement(controlApi.update) {
                    ok(addUpdate(actorAndProject, request))
                }

                implement(controlApi.chargeCredits) {
                    ok(chargeCredits(actorAndProject, request))
                }

                implement(controlApi.checkCredits) {
                    ok(chargeCredits(actorAndProject, request, checkOnly = true))
                }

                implement(controlApi.register) {
                    ok(register(actorAndProject, request))
                }
            }

            userApi.search?.let {
                implement(it) {
                    ok(search(actorAndProject, request))
                }
            }

            Unit
        }
    }
}
