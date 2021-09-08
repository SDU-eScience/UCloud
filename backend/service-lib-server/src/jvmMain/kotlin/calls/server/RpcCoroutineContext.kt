package calls.server

import dk.sdu.cloud.calls.server.IngoingCall
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class RpcCoroutineContext(
    val call: IngoingCall,
) : AbstractCoroutineContextElement(RpcCoroutineContext) {
    companion object Key : CoroutineContext.Key<RpcCoroutineContext>
}