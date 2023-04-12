package dk.sdu.cloud.faults

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.MicroAttributeKey
import dk.sdu.cloud.micro.MicroFeature
import dk.sdu.cloud.micro.MicroFeatureFactory
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.absoluteValue
import kotlin.random.Random

class FaultController(private val micro: Micro) : Controller {
    private var db: DBContext? = null
    private val dbMutex = Mutex()

    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        if (didImplement) return@with
        didImplement = true

        implement(FaultInjections.clearCaches) {
            val db = db ?: dbMutex.withLock {
                val currentDb = db
                if (currentDb != null) currentDb
                else {
                    val result = AsyncDBSessionFactory(micro)
                    db = result
                    result
                }
            }

            val knownCaches = ArrayList<SimpleCache<*, *>>()
            synchronized(SimpleCache.allCachesLock) {
                SimpleCache.allCachesOnlyForTestingPlease.forEach {
                    val cache = it.get()
                    if (cache != null) knownCaches.add(cache)
                }
            }

            knownCaches.forEach { it.clearAll() }

            // NOTE(Dan): This is a list of sequences which are likely to be used by a provider as a persistent
            // identifier, which might not be immediately cleaned
            val sequences = listOf(
                "provider.resource_id_seq",
                "provider.resource_update_id_seq",
            )

            for (attempt in 0 until 120) {
                db.withSession { session ->
                    sequenceStart += 1_000_000
                    for (seq in sequences) {
                        session.sendPreparedStatement(
                            {
                                setParameter("seq", seq)
                                setParameter("val", sequenceStart)
                            },
                            "select setval(:seq, :val, true)"
                        )
                    }
                }
            }

            ok(Unit)
        }
    }

    companion object {
        var sequenceStart = Random.nextLong().absoluteValue
        var didImplement = false
    }
}

class FaultInjectionFeature : MicroFeature {
    override fun init(ctx: Micro, serviceDescription: ServiceDescription, cliArgs: List<String>) {
        if (!ctx.developmentModeEnabled) return

        // TODO(Dan): This ends up creating a separate pool just for the fault injection feature
        val faults = FaultController(ctx)
        faults.configure(ctx.server)
    }

    companion object : MicroFeatureFactory<FaultInjectionFeature, Unit> {
        override val key: MicroAttributeKey<FaultInjectionFeature> = MicroAttributeKey("fault-injection")
        override fun create(config: Unit): FaultInjectionFeature = FaultInjectionFeature()
    }
}
