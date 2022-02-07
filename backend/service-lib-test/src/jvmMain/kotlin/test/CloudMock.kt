package dk.sdu.cloud.service.test

import dk.sdu.cloud.calls.AttributeContainer
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.IngoingCallResponse
import dk.sdu.cloud.calls.client.OutgoingCall
import dk.sdu.cloud.calls.client.OutgoingCallCompanion
import dk.sdu.cloud.calls.client.RpcClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.reflect.*

@Deprecated("Renamed", ReplaceWith("ClientMock"))
typealias CloudMock = ClientMock

class MockedOutgoingCall(override val attributes: AttributeContainer) : OutgoingCall

object ClientMock {
    @Suppress("EmptyFunctionBlock")
    @Deprecated("No longer needed")
    fun initialize() {
    }

    val client = mockk<RpcClient>()
    val authenticatedClient = AuthenticatedClient(client, object : OutgoingCallCompanion<OutgoingCall> {
        override val klass: KClass<OutgoingCall> = OutgoingCall::class
        override val attributes: AttributeContainer = AttributeContainer()
    }) {}

    @Suppress("UNCHECKED_CAST")
    inline fun <reified R : Any, S : Any, E : Any> mockCall(
        call: CallDescription<R, S, E>,
        crossinline resultCompute: MockedOutgoingCall.(R) -> TestCallResult<S, E>
    ) {
        coEvery {
            client.call<R, S, E, OutgoingCall, OutgoingCallCompanion<OutgoingCall>>(
                call,
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            val payload = invocation.args[1] as R
            //val beforeFilters = invocation.args[3] as (suspend (OutgoingCall) -> Unit)?
            val outgoingCall = MockedOutgoingCall(AttributeContainer())
            //runBlocking { beforeFilters?.invoke(outgoingCall) }
            val resp = resultCompute(outgoingCall, payload)

            when (resp) {
                is TestCallResult.Ok -> IngoingCallResponse.Ok(resp.result, resp.statusCode, outgoingCall)
                is TestCallResult.Error -> IngoingCallResponse.Error(resp.error, resp.statusCode, outgoingCall)
            }
        }
    }

    inline fun <reified R : Any, S : Any, E : Any> mockCallSuccess(
        call: CallDescription<R, S, E>,
        result: S,
        statusCode: HttpStatusCode = HttpStatusCode.OK
    ) {
        mockCall(call) { TestCallResult.Ok(result, statusCode) }
    }

    inline fun <reified R : Any, S : Any, E : Any> mockCallError(
        call: CallDescription<R, S, E>,
        error: E? = null,
        statusCode: HttpStatusCode
    ) {
        mockCall(call) { TestCallResult.Error(error, statusCode) }
    }

    @Suppress("EmptyFunctionBlock")
    @Deprecated("No longer needed")
    fun reset() {
    }
}

sealed class TestCallResult<S, E> {
    abstract val statusCode: HttpStatusCode

    data class Ok<S, E>(val result: S, override val statusCode: HttpStatusCode = HttpStatusCode.OK) :
        TestCallResult<S, E>()

    data class Error<S, E>(val error: E?, override val statusCode: HttpStatusCode) : TestCallResult<S, E>()
}
