package dk.sdu.cloud.file.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
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
import kotlin.reflect.KClass

class CommandRunnerFactoryForCalls<Ctx : FSUserContext>(
    private val underlyingFactory: FSCommandRunnerFactory<Ctx>,
    private val websocketSessionService: WSFileSessionService<Ctx>
) {
    val type: KClass<Ctx>
        get() = underlyingFactory.type

    suspend fun withCtx(
        callHandler: CallHandler<*, *, *>,
        user: String = callHandler.ctx.securityPrincipal.username,
        principalToVerify: SecurityPrincipal = callHandler.ctx.securityPrincipal,
        websockets: Boolean = true,
        http: Boolean = true,
        handler: suspend (Ctx) -> Unit
    ) {
        with(callHandler) {
            verifyPrincipal(principalToVerify)

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
        principalToVerify: SecurityPrincipal = callHandler.ctx.securityPrincipal,
        job: suspend (Ctx) -> CallResult<S, CommonErrorMessage>
    ) {
        with(callHandler) {
            verifyPrincipal(principalToVerify)

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
                        handleCallResult(it)
                    }

                    timeout.onAwait {
                        ok(LongRunningResponse.Timeout(), HttpStatusCode.Accepted)
                    }
                }
            }

            withContext<WSCall> {
                websocketSessionService.submitJob(ctx, user = user) {
                    handleCallResult(job(it))
                }
            }
        }
    }

    suspend fun createContext(callHandler: CallHandler<*, *, *>, username: String): Ctx {
        callHandler.verifyPrincipal(callHandler.ctx.securityPrincipal)
        return underlyingFactory(username)
    }

    private fun <S> CallHandler<*, LongRunningResponse<S>, CommonErrorMessage>.handleCallResult(
        it: CallResult<S, CommonErrorMessage>
    ) {
        when (it) {
            is CallResult.Success -> ok(LongRunningResponse.Result(it.item), it.status)
            is CallResult.Error -> error(it.item, it.status)
        }
    }

    private fun CallHandler<*, *, *>.verifyPrincipal(principal: SecurityPrincipal) {
        if (principal.role == Role.USER && principal.principalType == "password" &&
            !principal.twoFactorAuthentication
        ) {
            throw RPCException("2FA must be enabled before file services are allowed", HttpStatusCode.Forbidden)
        }

        if (principal.role in Roles.END_USER && !principal.serviceAgreementAccepted) {
            throw RPCException("Service license agreement not yet accepted", HttpStatusCode.Forbidden)
        }
    }

    companion object {
        private const val DELAY_IN_MILLIS = 10_000L
    }
}
