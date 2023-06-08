package dk.sdu.cloud.calls.server

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.microWhichIsConfiguringCalls
import dk.sdu.cloud.systemName
import io.ktor.http.*
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.random.Random
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
     * thrown (filter or implement handler). In case of an exception the [OutgoingCallResponse] will be set to
     * [OutgoingCallResponse.Error] with a null [OutgoingCallResponse.Error.error].
     *
     * It is _not_ possible to stop the pipeline from progressing at this point.
     */
    abstract class AfterResponse : IngoingCallFilter() {
        abstract suspend fun run(
            context: IngoingCall,
            call: CallDescription<*, *, *>,
            request: Any?,
            result: OutgoingCallResponse<*, *>,
            responseTimeMs: Long
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
            handler: suspend Ctx.(
                call: CallDescription<*, *, *>,
                request: Any,
                result: OutgoingCallResponse<*, *>
            ) -> Unit
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
            handler: suspend Ctx.(
                call: CallDescription<*, *, *>,
                request: Any?,
                result: OutgoingCallResponse<*, *>
            ) -> Unit
        ): AfterResponse {
            return object : AfterResponse() {
                override fun canUseContext(ctx: IngoingCall): Boolean {
                    return companion.klass.isInstance(ctx)
                }

                override suspend fun run(
                    context: IngoingCall,
                    call: CallDescription<*, *, *>,
                    request: Any?,
                    result: OutgoingCallResponse<*, *>,
                    responseTimeMs: Long
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
        override fun toString() = "$statusCode, ${result.toString().take(20_000)}"
    }

    class Error<S : Any, E : Any>(
        val error: E?,
        override val statusCode: HttpStatusCode
    ) : OutgoingCallResponse<S, E>() {
        override fun toString() = "$statusCode, ${error.toString().take(20_000)}"
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

    @Suppress("EmptyFunctionBlock")
    fun onStart() {
    }

    @Suppress("EmptyFunctionBlock")
    fun onStop() {
    }

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
    internal val delayedHandlers = ArrayList<DelayedHandler<*, *, *>>()
    var isRunning: Boolean = false
        private set

    fun attachFilter(serverFilter: IngoingCallFilter) {
        log.trace("Attaching filter: $serverFilter")
        filters.add(serverFilter)
    }

    fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>> attachRequestInterceptor(
        interceptor: IngoingRequestInterceptor<Ctx, Companion>
    ) {
        log.trace("Attaching interceptor for ${interceptor.companion}: $interceptor")
        requestInterceptors[interceptor.companion] = interceptor
    }

    private fun <R : Any, S : Any, E : Any> notifyInterceptors(delayedHandler: DelayedHandler<R, S, E>): Unit =
        with(delayedHandler) {
            log.trace("Adding call handler for $call")

            val existingHandler = handlers[call]
            if (existingHandler != null) {
                throw IllegalStateException("A handler for $call already exists!")
            }

            val interceptors: List<IngoingRequestInterceptor<*, *>> =
                if (requiredContext == null) {
                    requestInterceptors.values.toList()
                } else {
                    requiredContext.mapNotNull { requestInterceptors[it] }
                }

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
        val microImplementedBy = microWhichIsConfiguringCalls ?: error("no micro")
        call.microImplementedBy = microImplementedBy
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

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <Ctx : IngoingCall, Companion : IngoingCallCompanion<Ctx>,
            R : Any, S : Any, E : Any> handleIncomingCall(
        source: IngoingRequestInterceptor<Ctx, Companion>,
        call: CallDescription<R, S, E>,
        ctx: Ctx
    ) {
        val start = Time.now()
        var request: R? = null
        var response: OutgoingCallResponse<S, E>?

        requestCounter.labels(call.fullName).inc()
        requestsInFlight.labels(call.fullName).inc()

        @Suppress("TooGenericExceptionCaught")
        try {
            val handler = handlers[call] ?: run {
                log.error("Was asked to handle incoming call: $call but no such handler was found!")
                throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode.InternalServerError)
            }

            log.trace("Running BeforeParsing filters")
            val beforeParsing = filters.filterIsInstance<IngoingCallFilter.BeforeParsing>()
            beforeParsing.filter { it.canUseContext(ctx) }.forEach { it.run(ctx, call) }

            log.trace("Parsing call: $call")
            @Suppress("TooGenericExceptionCaught")
            val capturedRequest = try {
                val capturedRequest = source.parseRequest(ctx, call)
                request = capturedRequest
                capturedRequest
            } catch (ex: Exception) {
                when (ex) {
                    is RPCException -> throw ex
                    else -> {
                        log.debug("Suppressed parsing exception follows $call:")
                        log.debug(ex.stackTraceToString())
                        throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode.BadRequest)
                    }
                }
            }

            log.trace("Running AfterParsing filters")
            val afterParsing = filters.filterIsInstance<IngoingCallFilter.AfterParsing>()
            afterParsing.filter { it.canUseContext(ctx) }.forEach { it.run(ctx, call, capturedRequest) }

            val jobIdForDebug = ctx.jobIdOrNull?.take(4) ?: Random.nextInt(10_000).toString()

            log.info("Incoming call [$jobIdForDebug]: ${call.fullName}")

            val callHandler = CallHandler(ctx, capturedRequest, call).also { handler(it) }

            val responseResult = callHandler.result
            response = responseResult
            if (responseResult == null) {
                log.error("Did not receive a response from call handler: $call")
                throw RPCException.fromStatusCode(dk.sdu.cloud.calls.HttpStatusCode.InternalServerError)
            }

            log.trace("Running BeforeResponse filters")
            val beforeResponse = filters.filterIsInstance<IngoingCallFilter.BeforeResponse>()
            beforeResponse
                .filter { it.canUseContext(ctx) }
                .forEach { it.run(ctx, call, capturedRequest, responseResult) }

            log.debug("   Responding [$jobIdForDebug]: ${call.fullName}")

            source.produceResponse(ctx, call, responseResult)
        } catch (ex: Throwable) {
            val isEarlyClose = ex is ClosedSendChannelException ||
                    ex.javaClass.simpleName == "JobCancellationException" ||
                    ex.javaClass.simpleName == "CancellationException"

            if (ex !is RPCException && !isEarlyClose) {
                if (ex is NullPointerException && ex.message?.contains("Parameter specified as non-null") == true) {
                    // Ignore
                } else {
                    log.info("Uncaught exception in handler for $call")
                    log.info(ex.stackTraceToString())
                }
            }

            val statusCode = (ex as? RPCException)?.httpStatusCode ?: dk.sdu.cloud.calls.HttpStatusCode.InternalServerError
            val callResult =
                if (call.errorType.descriptor.serialName.endsWith("CommonErrorMessage") && ex is RPCException) {
                    val errorMessage = if (statusCode != dk.sdu.cloud.calls.HttpStatusCode.InternalServerError) {
                        CommonErrorMessage(ex.why, ex.errorCode)
                    } else {
                        log.warn("${call.fullName} $request Internal server error:\n${ex.stackTraceToString()}")
                        CommonErrorMessage("Internal Server Error", ex.errorCode)
                    }

                    @Suppress("UNCHECKED_CAST")
                    OutgoingCallResponse.Error(errorMessage as E, HttpStatusCode(statusCode.value, statusCode.description))
                } else if (isEarlyClose) {
                    OutgoingCallResponse.Error<S, E>(null, HttpStatusCode(499, "Connection closed early by client"))
                } else {
                    OutgoingCallResponse.Error<S, E>(null, HttpStatusCode(statusCode.value, statusCode.description))
                }
            log.debug("   Responding [${ctx.jobIdOrNull?.take(4)}]: ${call.fullName} ($callResult)")

            if (!isEarlyClose) {
                // Attempt to send a response (if possible). Silently discard any exception as it is likely a failure
                // on the wire.
                runCatching {
                    source.produceResponse(ctx, call, callResult)
                }
            }

            response = callResult
        }

        val responseOrDefault = response ?: OutgoingCallResponse.Error<S, E>(
            null,
            HttpStatusCode.InternalServerError
        )
        val afterResponse = filters.filterIsInstance<IngoingCallFilter.AfterResponse>()
        afterResponse
            .filter { it.canUseContext(ctx) }
            .forEach {
                @Suppress("TooGenericExceptionCaught")
                try {
                    it.run(ctx, call, request, responseOrDefault, Time.now() - start)
                } catch (ex: Throwable) {
                    if (ex is NullPointerException && ex.message?.contains("Parameter specified as non-null") == true) {
                        // Ignore
                    } else {
                        log.info("Uncaught exception in AfterResponse handler for $call")
                        log.info(ex.stackTraceToString())
                    }
                }
            }

        if (responseOrDefault.statusCode.isSuccess()) {
            requestsSuccessCounter.labels(call.fullName).inc()
        } else {
            requestsErrorCounter.labels(call.fullName).inc()
        }

        requestsInFlight.labels(call.fullName).dec()
    }

    companion object : Loggable {
        override val log = logger()

        data class DelayedHandler<R : Any, S : Any, E : Any>(
            val call: CallDescription<R, S, E>,
            val requiredContext: Set<IngoingCallCompanion<*>>?,
            val handler: suspend CallHandler<R, S, E>.() -> Unit
        )

        private val requestCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_server")
            .name("requests_started")
            .labelNames("request_name")
            .help("Total number of requests passing through RpcServer")
            .register()

        private val requestsSuccessCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_server")
            .name("requests_success")
            .labelNames("request_name")
            .help("Total number of requests which has passed through RpcServer successfully")
            .register()

        private val requestsErrorCounter = Counter.build()
            .namespace(systemName)
            .subsystem("rpc_server")
            .name("requests_error")
            .labelNames("request_name")
            .help("Total number of requests which has passed through RpcServer with a failure")
            .register()

        private val requestsInFlight = Gauge.build()
            .namespace(systemName)
            .subsystem("rpc_server")
            .name("requests_in_flight")
            .labelNames("request_name")
            .help("Number of requests currently in-flight in the RpcServer")
            .register()
    }
}
