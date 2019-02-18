package dk.sdu.cloud.micro

import dk.sdu.cloud.calls.client.HostInfo

data class RpcClientConfiguration(
    val host: HostInfo?,
    val http: Boolean?
)

data class RpcServerConfiguration(
    val http: Boolean?
)

data class RpcConfiguration(
    val client: RpcClientConfiguration?,
    val server: RpcServerConfiguration?
)

internal val Micro.rpcConfiguration: RpcConfiguration?
    get() = configuration.requestChunkAtOrNull("rpc")
