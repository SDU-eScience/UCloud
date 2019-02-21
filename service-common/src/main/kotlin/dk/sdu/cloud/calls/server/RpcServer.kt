package dk.sdu.cloud.calls.server

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode
import kotlin.reflect.KClass

interface IngoingCall {
    val attributes: AttributeContainer
}

interface IngoingCallCompanion<Ctx : IngoingCall> {
    val klass: KClass<Ctx>
    val attributes: AttributeContainer
}

interface IngoingContextFilter {
    fun canUseContext(ctx: IngoingCall): Boolean
}

sealed class IngoingCallFilter : IngoingContextFilter {
    /**
     * This filter runs before the parsing has taken place.
     *
     * It is possible to stop the pipeline from progressing at this point by throwing an exception.
     */
    abstract class BeforeParsing : IngoingCallFilter() {
        abstract suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>)
    }

    /**
     * This filter runs after the parsing has taken place.
     *
     * This filter might not run if a filter before it threw an exception.
     *
     * It is possible to stop the pipeline from progressing at this point by throwing an exception.
     */
    abstract class AfterParsing : IngoingCallFilter() {
        abstract suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any)
    }

    /**
     * This filter runs before the response.
     *
     * This filter might not run if a filter before it threw an exception.
     *
     * It is possible to stop the pipeline from progressing at this point by throwing an exception.
     */
    abstract class BeforeResponse : IngoingCallFilter() {
        abstract suspend fun run(
            context: IngoingCall,
            call: CallDescription<*, *, *>,
            request: Any,
            result: OutgoingCallResponse<*, *>
        )
    }

    /**
     * This filter runs after the response has been sent.
     *
     * This filter will _always_ run. This is regardless of any previous exception that may have been
     * thrown (filter or implement handler). In case of an exception the [OutgoingCallResponse] will be set to [OutgoingCallResponse.Error]
     * with a null [OutgoingCallResponse.Error.error].
     *
     * It is _not_ possible to stop the pipeline from progressing at this point.
     */
    abstract class AfterResponse : IngoingCallFilter() {
        abstract suspend fun run(
            context: IngoingCall,
            call: CallDescription<*, *, *>,
            request: Any?,
            result: OutgoingCallResponse<*, *>
        )
    }

    companion object {
        fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> beforeParsing(
            companion: Companion,
            handler: suspend Ctx.(call: CallDescription<*, *, *>) -> Unit
        ): BeforeParsing {
            return object : BeforeParsing() {
                override fun canUseContext(ctx: IngoingCall): Boolean {
                    return companion.klass.isInstance(ctx)
                }

                override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>) {
                    @Suppress("UNCHECKED_CAST")
                    context as Ctx
                    handler(context, call)
                }
            }
        }

        fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> afterParsing(
            companion: Companion,
            handler: suspend Ctx.(call: CallDescription<*, *, *>, request: Any) -> Unit
        ): AfterParsing {
            return object : AfterParsing() {
                override fun canUseContext(ctx: IngoingCall): Boolean {
                    return companion.klass.isInstance(ctx)
                }

                override suspend fun run(context: IngoingCall, call: CallDescription<*, *, *>, request: Any) {
                    @Suppress("UNCHECKED_CAST")
                    context as Ctx
                    handler(context, call, request)
                }
            }
        }

        fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> beforeResponse(
            companion: Companion,
            handler: suspend Ctx.(call: CallDescription<*, *, *>, request: Any, result: OutgoingCallResponse<*, *>) -> Unit
        ): BeforeResponse {
            return object : BeforeResponse() {
                override fun canUseContext(ctx: IngoingCall): Boolean {
                    return companion.klass.isInstance(ctx)
                }

                override suspend fun run(
                    context: IngoingCall,
                    call: CallDescription<*, *, *>,
                    request: Any,
                    result: OutgoingCallResponse<*, *>
                ) {
                    @Suppress("UNCHECKED_CAST")
                    context as Ctx
                    handler(context, call, request, result)
                }
            }
        }

        fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> afterResponse(
            companion: Companion,
            handler: suspend Ctx.(call: CallDescription<*, *, *>, request: Any?, result: OutgoingCallResponse<*, *>) -> Unit
        ): AfterResponse {
            return object : AfterResponse() {
                override fun canUseContext(ctx: IngoingCall): Boolean {
                    return companion.klass.isInstance(ctx)
                }

                override suspend fun run(
                    context: IngoingCall,
                    call: CallDescription<*, *, *>,
                    request: Any?,
                    result: OutgoingCallResponse<*, *>
                ) {
                    @Suppress("UNCHECKED_CAST")
                    context as Ctx
                    handler(context, call, request, result)
                }
            }
        }
    }
}

sealed class OutgoingCallResponse<S : Any, E : Any> {
    abstract val statusCode: HttpStatusCode

    class Ok<S : Any, E : Any>(
        val result: S,
        override val statusCode: HttpStatusCode
    ) : OutgoingCallResponse<S, E>() {
        override fun toString() = "OutgoingCallResponse.Ok($statusCode, ${result.toString().take(100)})"
    }

    class Error<S : Any, E : Any>(
        val error: E?,
        override val statusCode: HttpStatusCode
    ) : OutgoingCallResponse<S, E>() {
        override fun toString() = "OutgoingCallResponse.Error($statusCode, ${error.toString().take(100)})"
    }

    /**
     * Indicates that content was delivered externally.
     *
     * This means that the content was delivered directly to the underlying medium and does not need to be
     * produced in [IngoingRequestInterceptor.produceResponse].
     */
    class AlreadyDelivered<S : Any, E : Any> : OutgoingCallResponse<S, E>() {
        override val statusCode: HttpStatusCode = HttpStatusCode.OK
    }
}

class CallHandler<R : Any, S : Any, E : Any> internal constructor(
    val ctx: IngoingCall,
    val request: R,
    val description: CallDescription<R, S, E>
) {
    internal var result: OutgoingCallResponse<S, E>? = null

    fun ok(result: S, statusCode: HttpStatusCode = HttpStatusCode.OK) {
        if (this.result != null) throw IllegalStateException("Cannot set call result twice!")
        this.result = OutgoingCallResponse.Ok(result, statusCode)
    }

    @Deprecated(replaceWith = ReplaceWith("okContentAlreadyDelivered()"), message = "New name")
    fun okContentDeliveredExternally() {
        okContentAlreadyDelivered()
    }

    fun okContentAlreadyDelivered() {
        if (this.result != null) throw IllegalStateException("Cannot set call result twice!")
        this.result = OutgoingCallResponse.AlreadyDelivered()
    }

    fun error(error: E, statusCode: HttpStatusCode) {
        if (this.result != null) throw IllegalStateException("Cannot set call result twice!")
        this.result = OutgoingCallResponse.Error(error, statusCode)
    }
}

interface IngoingRequestInterceptor<Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> {
    val companion: Companion

    fun onStart() {}

    fun onStop() {}

    /**
     * Notifies the interceptor that it should start listening for calls that match the description in [call].
     *
     * Implementations should call [RpcServer.handleIncomingCall] when a call matching this signature is intercepted.
     * They should also be ready to intercept exceptions of type [RPCException]. Exceptions should always result in
     * a proper error message. If the [CallDescription.errorType] is [CommonErrorMessage] then implementations should
     * attempt to set a proper error message as well.
     */
    fun addCallListenerForCall(call: CallDescription<*, *, *>)

    /**
     * Requests that the interceptor should parse a call.
     */
    suspend fun <R : Any> parseRequest(ctx: Ctx, call: CallDescription<R, *, *>): R

    /**
     * Requests that the interceptor should produce a result.
     */
    suspend fun <R : Any, S : Any, E : Any> produceResponse(
        ctx: Ctx,
        call: CallDescription<R, S, E>,
        callResult: OutgoingCallResponse<S, E>
    )
}

class RpcServer {
    private val requestInterceptors = HashMap<IngoingCallCompanion<*>, IngoingRequestInterceptor<*, *>>()
    private val filters = ArrayList<IngoingCallFilter>()
    private val handlers = HashMap<CallDescription<*, *, *>, suspend CallHandler<*, *, *>.() -> Unit>()
    private val delayedHandlers = ArrayList<DelayedHandler<*, *, *>>()
    var isRunning: Boolean = false
        private set

    private data class DelayedHandler<R : Any, S : Any, E : Any>(
        val call: CallDescription<R, S, E>,
        val requiredContext: Set<IngoingCallCompanion<*>>?,
        val handler: suspend CallHandler<R, S, E>.() -> Unit
    )

    fun attachFilter(serverFilter: IngoingCallFilter) {
        log.debug("Attaching filter: $serverFilter")
        filters.add(serverFilter)
    }

    fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> attachRequestInterceptor(
        interceptor: IngoingRequestInterceptor<Ctx, Companion>
    ) {
        log.debug("Attaching interceptor for ${interceptor.companion}: $interceptor")
        requestInterceptors[interceptor.companion] = interceptor
    }

    private fun <R : Any, S : Any, E : Any> notifyInterceptors(delayedHandler: DelayedHandler<R, S, E>): Unit =
        with(delayedHandler) {
            log.debug("Adding call handler for $call")

            val existingHandler = handlers[call]
            if (existingHandler != null) {
                throw IllegalStateException("A handler for $call already exists!")
            }

            val interceptors: List<IngoingRequestInterceptor<*, *>> =
                if (requiredContext == null) requestInterceptors.values.toList()
                else requiredContext.mapNotNull { requestInterceptors[it] }

            if (interceptors.isEmpty()) {
                log.info("Unable to find any request interceptor for $call")
                log.info("This call will never be handled!")
            }

            interceptors.forEach { it.addCallListenerForCall(call) }

            @Suppress("UNCHECKED_CAST")
            handlers[call] = handler as (suspend CallHandler<*, *, *>.() -> Unit)

            return@with
        }

    fun <R : Any, S : Any, E : Any> implement(
        call: CallDescription<R, S, E>,
        requiredContext: Set<IngoingCallCompanion<*>>? = null,
        handler: suspend CallHandler<R, S, E>.() -> Unit
    ) {
        delayedHandlers.add(DelayedHandler(call, requiredContext, handler))
    }

    fun start() {
        isRunning = true
        delayedHandlers.forEach { notifyInterceptors(it) }
        delayedHandlers.clear()
        requestInterceptors.values.forEach { it.onStart() }
    }

    fun stop() {
        requestInterceptors.values.forEach { it.onStop() }
        isRunning = false
    }

    suspend fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>,
            R : Any, S : Any, E : Any> handleIncomingCall(
        source: IngoingRequestInterceptor<Ctx, Companion>,
        call: CallDescription<R, S, E>,
        ctx: Ctx
    ) {
        var request: R? = null
        var response: OutgoingCallResponse<S, E>? = null

        try {
            val handler = handlers[call] ?: run {
                log.error("Was asked to handle incoming call: $call but no such handler was found!")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            log.debug("Running BeforeParsing filters")
            val beforeParsing = filters.filterIsInstance<IngoingCallFilter.BeforeParsing>()
            beforeParsing.filter { it.canUseContext(ctx) }.forEach { it.run(ctx, call) }

            log.debug("Parsing call: $call")
            val capturedRequest = try {
                val capturedRequest = source.parseRequest(ctx, call)
                request = capturedRequest
                capturedRequest
            } catch (ex: Exception) {
                when (ex) {
                    is RPCException -> throw ex
                    else -> {
                        log.debug("Suppressed parsing exception follows:")
                        log.debug(ex.stackTraceToString())
                        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }
                }
            }

            log.debug("Running AfterParsing filters")
            val afterParsing = filters.filterIsInstance<IngoingCallFilter.AfterParsing>()
            afterParsing.filter { it.canUseContext(ctx) }.forEach { it.run(ctx, call, capturedRequest) }

            log.info("Incoming call: $call ($request)")
            val callHandler = CallHandler(ctx, capturedRequest, call).also { handler(it) }

            val responseResult = callHandler.result
            response = responseResult
            if (responseResult == null) {
                log.error("Did not receive a response from call handler: $call")
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
            }

            log.debug("Running BeforeResponse filters")
            val beforeResponse = filters.filterIsInstance<IngoingCallFilter.BeforeResponse>()
            beforeResponse
                .filter { it.canUseContext(ctx) }
                .forEach { it.run(ctx, call, capturedRequest, responseResult) }
            log.debug("Result of $call is $responseResult")
            source.produceResponse(ctx, call, responseResult)
        } catch (ex: Throwable) {
            if (ex !is RPCException) {
                log.info("Uncaught exception in handler for $call")
                log.info(ex.stackTraceToString())
            } else {
                log.debug(ex.stackTraceToString())
            }

            val statusCode = (ex as? RPCException)?.httpStatusCode ?: HttpStatusCode.InternalServerError
            if (call.errorType.type == CommonErrorMessage::class.java && ex is RPCException) {
                val errorMessage = if (statusCode != HttpStatusCode.InternalServerError) CommonErrorMessage(ex.why)
                else CommonErrorMessage("Internal Server Error")

                @Suppress("UNCHECKED_CAST")
                source.produceResponse(
                    ctx, call,
                    OutgoingCallResponse.Error(errorMessage as E, statusCode)
                )
            } else {
                source.produceResponse(ctx, call, OutgoingCallResponse.Error<S, E>(null, statusCode))
            }
        }

        val responseOrDefault = response ?: OutgoingCallResponse.Error<S, E>(
            null,
            HttpStatusCode.InternalServerError
        )
        val afterResponse = filters.filterIsInstance<IngoingCallFilter.AfterResponse>()
        afterResponse
            .filter { it.canUseContext(ctx) }
            .forEach {
                try {
                    it.run(ctx, call, request, responseOrDefault)
                } catch (ex: Throwable) {
                    log.info("Uncaught exception in AfterResponse handler for $call")
                    log.info(ex.stackTraceToString())
                }
            }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
