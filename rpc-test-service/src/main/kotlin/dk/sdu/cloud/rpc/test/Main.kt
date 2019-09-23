package dk.sdu.cloud.rpc.test

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.subscribe
import dk.sdu.cloud.calls.server.FrontendOverrides
import dk.sdu.cloud.micro.ClientFeature
import dk.sdu.cloud.micro.ConfigurationFeature
import dk.sdu.cloud.micro.DeinitFeature
import dk.sdu.cloud.micro.DevelopmentOverrides
import dk.sdu.cloud.micro.HibernateFeature
import dk.sdu.cloud.micro.KtorServerProviderFeature
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.RedisFeature
import dk.sdu.cloud.micro.ScriptFeature
import dk.sdu.cloud.micro.ServerFeature
import dk.sdu.cloud.micro.ServiceDiscoveryOverrides
import dk.sdu.cloud.micro.ServiceInstanceFeature
import dk.sdu.cloud.micro.TokenValidationFeature
import dk.sdu.cloud.micro.initWithDefaultFeatures
import dk.sdu.cloud.micro.install
import dk.sdu.cloud.micro.runScriptHandler
import dk.sdu.cloud.rpc.test.api.RpcTestServiceDescription
import dk.sdu.cloud.rpc.test.api.TestA
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if ("--ws-client" in args) {
        val micro = Micro().apply {
            init(RpcTestServiceDescription, args)

            install(DeinitFeature)
            install(ScriptFeature)
            install(ConfigurationFeature)
            install(ServiceDiscoveryOverrides)
            install(ServiceInstanceFeature)
            install(DevelopmentOverrides)
            install(ClientFeature)
            install(TokenValidationFeature)
            install(RefreshingJWTCloudFeature)
        }

        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)
        runBlocking {
            val resp = TestA.pingSelf.subscribe(Unit, wsClient) { message ->
                println(message)
            }

            println(resp)
        }

        exitProcess(0)
    } else {
        val micro = Micro().apply {
            initWithDefaultFeatures(RpcTestServiceDescription, args)
            install(HibernateFeature)
            install(RefreshingJWTCloudFeature)
        }

        if (micro.runScriptHandler()) return

        Server(micro).start()
    }
}
