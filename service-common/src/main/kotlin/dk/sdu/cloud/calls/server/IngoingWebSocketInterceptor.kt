package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription

class WSCall : IngoingCall {
    override val attributes = AttributeContainer()

    companion object : IngoingCallCompanion<WSCall> {
        override val klass = WSCall::class
        override val attributes = AttributeContainer()
    }
}

class IngoingWebSocketInterceptor : IngoingRequestInterceptor<WSCall, WSCall.Companion> {
    override val companion = WSCall

    override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun <R : Any> parseRequest(ctx: WSCall, call: CallDescription<R, *, *>): R {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: WSCall,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
