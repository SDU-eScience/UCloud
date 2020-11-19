package dk.sdu.cloud.app.kubernetes.rpc

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.app.kubernetes.api.KubernetesCompute
import dk.sdu.cloud.app.kubernetes.services.JobManagement
import dk.sdu.cloud.app.kubernetes.services.K8LogService
import dk.sdu.cloud.app.kubernetes.services.proxy.VncService
import dk.sdu.cloud.app.kubernetes.services.proxy.WebService
import dk.sdu.cloud.app.orchestrator.api.ProviderManifest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.events.EventStreamContainer
import dk.sdu.cloud.service.BroadcastingStream
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.collections.HashMap

private object CancelWSStream : EventStreamContainer() {
    val events = stream<FindByStringId>("appk8-ws-cancel", { "id" })
}

class AppKubernetesController(
    private val jobManagement: JobManagement,
    private val logService: K8LogService,
    private val vncService: VncService,
    private val webService: WebService,
    private val broadcastStream: BroadcastingStream
) : Controller {
    private val streams = HashMap<String, ReceiveChannel<*>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        runBlocking {
            broadcastStream.subscribe(CancelWSStream.events) { (id) ->
                streams.remove(id)?.cancel()
            }
        }

        implement(KubernetesCompute.create) {
            jobManagement.create(request)
            ok(Unit)
        }

        implement(KubernetesCompute.delete) {
            jobManagement.cancel(request)
            ok(Unit)
        }

        implement(KubernetesCompute.extend) {
            jobManagement.extend(request)
            ok(Unit)
        }

        implement(KubernetesCompute.suspend) {
            throw RPCException("UCloud/Compute does not support job suspension", HttpStatusCode.BadRequest)
        }

        implement(KubernetesCompute.verify) {
            jobManagement.verifyJobs(request.items)
            ok(Unit)
        }

        implement(KubernetesCompute.retrieveManifest) {
            ok(
                ProviderManifest().apply {
                    with(features) {
                        with(compute) {
                            with(docker) {
                                enabled = true
                                web = true
                                vnc = true
                                batch = true
                                logs = true
                                terminal = true
                            }
                        }
                    }
                }
            )
        }

        return@configure
    }

    companion object : Loggable {
        override val log = logger()
    }
}
