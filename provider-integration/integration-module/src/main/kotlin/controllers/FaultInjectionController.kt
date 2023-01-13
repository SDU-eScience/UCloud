package dk.sdu.cloud.controllers

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.FixedOutgoingHostResolver
import dk.sdu.cloud.calls.client.HostInfo
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingHttpRequestInterceptor
import dk.sdu.cloud.calls.client.RpcClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.outgoingTargetHost
import dk.sdu.cloud.calls.server.RpcServer
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.faults.FaultInjections
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.SimpleCache
import dk.sdu.cloud.sql.TemporaryView
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.random.Random

class FaultInjectionController(
    private val controllerContext: ControllerContext,
) : Controller {
    override fun configure(rpcServer: RpcServer): Unit = with(rpcServer) {
        if (!controllerContext.configuration.core.developmentMode) return@with

        val rpcClient = RpcClient().apply {
            OutgoingHttpRequestInterceptor().install(
                this,
                FixedOutgoingHostResolver(HostInfo("localhost", "http", 8080))
            )
        }

        var currentHost = HostInfo("localhost", "http", 8080)
        val client = AuthenticatedClient(
            rpcClient,
            OutgoingHttpCall,
            authenticator = {},
            afterHook = {
                it.attributes.outgoingTargetHost = currentHost
            }
        )

        implement(FaultInjections.clearCaches) {
            val knownCaches = ArrayList<SimpleCache<*, *>>()
            synchronized(SimpleCache.allCachesLock) {
                SimpleCache.allCachesOnlyForTestingPlease.forEach {
                    val cache = it.get()
                    if (cache != null) knownCaches.add(cache)
                }
            }

            knownCaches.onEach { it.clearAll() }

            TemporaryView.notifyTestReset()

            if (controllerContext.configuration.shouldRunServerCode()) {
                for (port in allocatedUserInstancePorts()) {
                    currentHost = currentHost.copy(port = port)
                    runCatching {
                        FaultInjections.clearCaches.call(
                            Unit,
                            client
                        )
                    }
                }

                // NOTE(Dan): This function is typically invoked after a database snapshot has been restored. This
                // typically causes an interruption to the DB services for several seconds. Here we attempt to retry
                // until the database is operational again.
                for (attempt in 0 until 30) {
                    val databaseIsFunctional = runCatching {
                        dbConnection.withSession { session ->
                            session.prepareStatement("select 1").useAndInvokeAndDiscard()
                        }
                    }.isSuccess

                    if (databaseIsFunctional) break
                    if (attempt >= 10) {
                        log.warn("Database is still not operational after a test snapshot restoration! " +
                                "We have been waiting for at least $attempt seconds.")
                    }

                    delay(1000)
                }
            }

            controllerContext.configuration.plugins.jobs.values.forEach {
                try {
                    it.resetTestData()
                } catch (ex: Throwable) {
                    log.warn("Failed at resetting test data: ${ex.stackTraceToString()}")
                }
            }

            if (controllerContext.configuration.shouldRunServerCode()) {
                sequenceValue += 10_000
                dbConnection.withSession { session ->
                    session.prepareStatement(
                        """
                            select setval('simple_project_group_mapper_local_id_seq', :val, true)
                        """
                    ).useAndInvokeAndDiscard {
                        bindInt("val", sequenceValue)
                    }
                }
            }

            ok(Unit)
        }
    }

    companion object : Loggable {
        override val log = logger()

        private var sequenceValue = Random.nextInt().absoluteValue
    }
}
