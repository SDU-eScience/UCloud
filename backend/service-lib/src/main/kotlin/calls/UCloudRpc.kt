package dk.sdu.cloud.calls

enum class UCloudRpcSubsystem {
    USER,
    PAM,
    ORCHESTRATOR,
    META,
    EXTERNAL,
}

class UCloudRpcRequest<R : Any, S : Any, E : Any> internal constructor(
    val context: CallDescription<R, S, E>,
    val subsystem: UCloudRpcSubsystem,
) {
    companion object {
        internal val callKey = AttributeKey<UCloudRpcRequest<*, *, *>>("websocket-request")

        const val OP_REQ = 1.toByte()
        const val OP_REG_CALL = 2.toByte()
        const val OP_REG_BEARER = 3.toByte()
        const val OP_REG_INTENT = 4.toByte()
        const val OP_REG_PROJECT = 5.toByte()
        const val OP_RESP = 6.toByte()
    }
}

fun <R : Any, S : Any, E : Any> CallDescription<R, S, E>.ucloudRpc(
    component: UCloudRpcSubsystem,
) {
    attributes[UCloudRpcRequest.callKey] = UCloudRpcRequest(this, component)
}

@Suppress("UNCHECKED_CAST")
val <R : Any, S : Any, E : Any> CallDescription<R, S, E>.ucloudRpc: UCloudRpcRequest<R, S, E>
    get() = attributes[UCloudRpcRequest.callKey] as UCloudRpcRequest<R, S, E>
