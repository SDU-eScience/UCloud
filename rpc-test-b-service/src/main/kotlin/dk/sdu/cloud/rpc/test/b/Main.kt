package dk.sdu.cloud.rpc.test.b

import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.rpc.test.b.api.RpcTestBServiceDescription
import dk.sdu.cloud.micro.*

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(RpcTestBServiceDescription, args)
        install(HibernateFeature)
        install(RefreshingJWTCloudFeature)
    }

    if (micro.runScriptHandler()) return

    Server(micro).start()
}
