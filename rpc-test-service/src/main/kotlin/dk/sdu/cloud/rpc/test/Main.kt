package dk.sdu.cloud.rpc.test

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.rpc.test.api.RpcTestServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(RpcTestServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
