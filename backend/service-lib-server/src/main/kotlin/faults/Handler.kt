package dk.sdu.cloud.faults

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.MicroAttributeKey
import dk.sdu.cloud.micro.MicroFeature
import dk.sdu.cloud.micro.MicroFeatureFactory
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.SimpleCache

class FaultController : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        implement(FaultInjections.clearCaches) {
            val knownCaches = ArrayList<SimpleCache<*, *>>()
            synchronized(SimpleCache.allCachesLock) {
                SimpleCache.allCachesOnlyForTestingPlease.forEach {
                    val cache = it.get()
                    if (cache != null) knownCaches.add(cache)
                }
            }

            knownCaches.onEach { it.clearAll() }
            ok(Unit)
        }
    }
}

class FaultInjectionFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        if (!ctx.developmentModeEnabled) return

        val faults = FaultController()
        faults.configure(ctx.server)
    }

    companion object : MicroFeatureFactory<FaultInjectionFeature, Unit> {
        override val key: MicroAttributeKey<FaultInjectionFeature> = MicroAttributeKey("fault-injection")
        override fun create(config: Unit): FaultInjectionFeature = FaultInjectionFeature()
    }
}
