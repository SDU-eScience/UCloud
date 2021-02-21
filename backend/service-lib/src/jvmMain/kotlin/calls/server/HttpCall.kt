package dk.sdu.cloud.calls.server

import dk.sdu.cloud.calls.AttributeContainer
import io.ktor.application.ApplicationCall
import io.ktor.util.pipeline.PipelineContext
import kotlin.reflect.KClass

class HttpCall(
    private val ctx: PipelineContext<Any, ApplicationCall>
) : IngoingCall, PipelineContext<Any, ApplicationCall> by ctx {
    override val attributes = AttributeContainer()

    companion object : IngoingCallCompanion<HttpCall> {
        override val klass: KClass<HttpCall> = HttpCall::class
        override val attributes: AttributeContainer = AttributeContainer()
    }
}
