package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeContainer
import io.ktor.server.application.*
import io.ktor.util.pipeline.PipelineContext
import kotlin.reflect.KClass

class HttpCall(
    val ktor: PipelineContext<Any, ApplicationCall>
) : IngoingCall {
    override val attributes = AttributeContainer()

    companion object : IngoingCallCompanion<HttpCall> {
        override val klass: KClass<HttpCall> = HttpCall::class
        override val attributes: AttributeContainer = AttributeContainer()
    }
}
