package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.WSCall
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.calls.server.withContext
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.FSUserContext
import dk.sdu.cloud.file.services.WSFileSessionService
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.CallResult
import dk.sdu.cloud.file.util.handleFSException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

class CommandRunnerFactoryForCalls<Ctx : FSUserContext>(
    private val underlyingFactory: FSCommandRunnerFactory<Ctx>,
    private val websocketSessionService: WSFileSessionService<Ctx>
) {
    suspend fun withCtx(
        callHandler: CallHandler<*, *, *>,
        user: String = callHandler.ctx.securityPrincipal.username,
        websockets: Boolean = true,
        http: Boolean = true,
        handler: suspend (Ctx) -> Unit
    ) {
        with(callHandler) {
            if (websockets) {
                withContext<WSCall> {
                    websocketSessionService.submitJob(ctx, user) { handler(it) }
                }
            }

            if (http) {
                withContext<HttpCall> {
                    underlyingFactory.withContext(user) { handler(it) }
                }
            }
        }
    }

    suspend fun <S> withCtxAndTimeout(
        callHandler: CallHandler<*, LongRunningResponse<S>, CommonErrorMessage>,
        user: String = callHandler.ctx.securityPrincipal.username,
        job: suspend (Ctx) -> CallResult<S, CommonErrorMessage>
    ) {
        with(callHandler) {
            withContext<HttpCall> {
                val result: Deferred<CallResult<S, CommonErrorMessage>> = GlobalScope.async {
                    try {
                        underlyingFactory.withContext(user) { job(it) }
                    } catch (ex: Exception) {
                        val (msg, status) = handleFSException(ex)
                        CallResult.Error<S, CommonErrorMessage>(msg, status)
                    }
                }

                val timeout = GlobalScope.async { delay(DELAY_IN_MILLIS) }

                select<Unit> {
                    result.onAwait {
                        when (it) {
                            is CallResult.Success -> ok(LongRunningResponse.Result(it.item), it.status)
                            is CallResult.Error -> error(it.item, it.status)
                        }
                    }

                    timeout.onAwait {
                        ok(LongRunningResponse.Timeout(), HttpStatusCode.Accepted)
                    }
                }
            }

            withContext<WSCall> {
                websocketSessionService.submitJob(ctx, user = user) { job(it) }
            }
        }
    }

    companion object {
        private const val DELAY_IN_MILLIS = 10_000L
    }
}
