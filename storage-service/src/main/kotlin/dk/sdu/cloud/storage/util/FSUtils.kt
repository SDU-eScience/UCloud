package dk.sdu.cloud.storage.util

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSResult
import dk.sdu.cloud.storage.services.FSUserContext
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.selects.select
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

fun homeDirectory(user: String): String = "/home/$user/"

fun homeDirectory(ctx: FSUserContext): String = homeDirectory(ctx.user)

fun favoritesDirectory(user: String): String = joinPath(homeDirectory(user), "Favorites", isDirectory = true)

fun favoritesDirectory(ctx: FSUserContext): String = favoritesDirectory(ctx.user)

fun joinPath(vararg components: String, isDirectory: Boolean = false): String {
    return File(components.joinToString("/") + (if (isDirectory) "/" else "")).normalize().path
}

fun String.parents(): List<String> {
    val components = components().dropLast(1)
    return components.mapIndexed { index, _ ->
        val path = "/" + components.subList(0, index + 1).joinToString("/").removePrefix("/")
        if (path == "/") path else "$path/"
    }
}

fun String.parent(): String {
    val components = components().dropLast(1)
    if (components.isEmpty()) return "/"

    val path = "/" + components.joinToString("/").removePrefix("/")
    return if (path == "/") path else "$path/"
}

fun String.components(): List<String> = removeSuffix("/").split("/")

fun String.fileName(): String = File(this).name

fun String.normalize(): String = File(this).normalize().path

fun relativize(rootPath: String, absolutePath: String): String {
    return File(rootPath).toURI().relativize(File(absolutePath).toURI()).normalize().path
}

fun <T> FSResult<T>.unwrap(): T {
    if (statusCode != 0) {
        throwExceptionBasedOnStatus(statusCode)
    } else {
        return value
    }
}

fun throwExceptionBasedOnStatus(status: Int): Nothing {
    when (status.absoluteValue) {
        // TODO Constants for errnos
        1, 20, 21, 22 -> throw FSException.BadRequest()
        2, 93 -> throw FSException.NotFound()
        5, 6, 16, 19, 23, 24, 27, 28, 30, 31 -> throw FSException.IOException()
        13 -> throw FSException.PermissionException()
        17 -> throw FSException.AlreadyExists()

        else -> throw FSException.CriticalException("Unknown status code $status")
    }
}

sealed class FSException(override val message: String, val isCritical: Boolean = false) : RuntimeException() {
    data class BadRequest(val why: String = "") : FSException("Bad request $why")
    data class NotFound(val file: String? = null) : FSException("Not found ${file ?: ""}")
    data class AlreadyExists(val file: String? = null) : FSException("Already exists ${file ?: ""}")
    class PermissionException : FSException("Permission denied")
    class CriticalException(why: String) : FSException("Critical exception: $why", true)
    class IOException : FSException("Internal server error (IO)", true)
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage, *>.tryWithFS(
    body: () -> Unit
) {
    try {
        body()
    } catch (ex: Exception) {
        fsLog.debug(ex.stackTraceToString())
        val (msg, status) = handleFSException(ex)
        error(msg, status)
    }
}

suspend inline fun <Ctx : FSUserContext> RESTHandler<*, *, CommonErrorMessage, *>.tryWithFS(
    factory: FSCommandRunnerFactory<Ctx>,
    user: String,
    body: (Ctx) -> Unit
) {
    try {
        factory.withContext(user) { body(it) }
    } catch (ex: Exception) {
        fsLog.debug(ex.stackTraceToString())
        val (msg, status) = handleFSException(ex)
        error(msg, status)
    }
}

sealed class CallResult<S, E>(val status: HttpStatusCode) {
    class Success<S, E>(val item: S, status: HttpStatusCode = HttpStatusCode.OK) : CallResult<S, E>(status)
    class Error<S, E>(val item: E, status: HttpStatusCode) : CallResult<S, E>(status)
}

suspend fun <Ctx : FSUserContext, S> RESTHandler<*, LongRunningResponse<S>, CommonErrorMessage, *>.tryWithFSAndTimeout(
    factory: FSCommandRunnerFactory<Ctx>,
    user: String,
    job: suspend (Ctx) -> CallResult<S, CommonErrorMessage>
) {
    val result: Deferred<CallResult<S, CommonErrorMessage>> = async {
        try {
            factory.withContext(user) { job(it) }
        } catch (ex: Exception) {
            val (msg, status) = handleFSException(ex)
            CallResult.Error<S, CommonErrorMessage>(msg, status)
        }
    }

    val timeout = async { delay(10, TimeUnit.SECONDS) }

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

fun handleFSException(ex: Exception): Pair<CommonErrorMessage, HttpStatusCode> {
    return when (ex) {
        is FSException -> {
            // Enforce that we must handle all cases. Will cause a compiler error if we don't cover all
            when (ex) {
                is FSException.NotFound -> Pair(CommonErrorMessage(ex.message), HttpStatusCode.NotFound)
                is FSException.BadRequest -> Pair(CommonErrorMessage(ex.message), HttpStatusCode.BadRequest)
                is FSException.AlreadyExists -> Pair(CommonErrorMessage(ex.message), HttpStatusCode.Conflict)
                is FSException.PermissionException -> Pair(
                    CommonErrorMessage(ex.message),
                    HttpStatusCode.Forbidden
                )
                is FSException.CriticalException, is FSException.IOException -> {
                    fsLog.warn("Caught critical FS exception!")
                    fsLog.warn(ex.stackTraceToString())
                    Pair(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
                }
            }
        }

        is IllegalArgumentException -> {
            Pair(CommonErrorMessage("Bad request"), HttpStatusCode.BadRequest)
        }

        is TooManyRetries -> {
            handleFSException(ex.causes.first())
        }

        else -> {
            fsLog.warn("Unknown FS exception!")
            fsLog.warn(ex.stackTraceToString())
            Pair(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
        }
    }
}

val fsLog = LoggerFactory.getLogger("dk.sdu.cloud.storage.services.FileSystemServiceKt")!!
