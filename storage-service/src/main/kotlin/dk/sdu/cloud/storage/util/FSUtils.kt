package dk.sdu.cloud.storage.util

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.file.api.LongRunningResponse
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.services.FSCommandRunnerFactory
import dk.sdu.cloud.storage.services.FSResult
import dk.sdu.cloud.storage.services.FSUserContext
import dk.sdu.cloud.storage.services.withContext
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.selects.select
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOError
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

private const val OPERATION_NOT_PERMITED = 1
private const val NO_SUCH_FILE_OR_DIR = 2
private const val IO_ERROR = 5
private const val NO_SUCH_DEVICE_OR_ADDRESS = 6
private const val PERMISSION_DENIED = 13
private const val DEVICE_OR_RESOURCE_BUSY = 16
private const val FILE_EXISTS = 17
private const val NO_SUCH_DEVICE = 19
private const val NOT_A_DIRECTORY = 20
private const val IS_A_DIRECTORY = 21
private const val INVALID_ARGUMENT = 22
private const val FILE_TABLE_OVERFLOW = 23
private const val TOO_MANY_OPEN_FILES = 24
private const val FILE_TOO_LARGE = 27
private const val NO_SPACE_LEFT_ON_DEVICE = 28
private const val READ_ONLY_FILE_SYSTEM = 30
private const val TOO_MANY_LINKS = 31
private const val PROTOCOL_NOT_SUPPORTED = 93


fun throwExceptionBasedOnStatus(status: Int): Nothing {
    when (status.absoluteValue) {
        OPERATION_NOT_PERMITED, NOT_A_DIRECTORY, IS_A_DIRECTORY, INVALID_ARGUMENT -> throw FSException.BadRequest()

        NO_SUCH_FILE_OR_DIR, PROTOCOL_NOT_SUPPORTED -> throw FSException.NotFound()

        IO_ERROR, NO_SUCH_DEVICE_OR_ADDRESS, DEVICE_OR_RESOURCE_BUSY,
        NO_SUCH_DEVICE, FILE_TABLE_OVERFLOW, TOO_MANY_OPEN_FILES, FILE_TOO_LARGE,
        NO_SPACE_LEFT_ON_DEVICE, READ_ONLY_FILE_SYSTEM, TOO_MANY_LINKS -> throw FSException.IOException()

        PERMISSION_DENIED -> throw FSException.PermissionException()

        FILE_EXISTS -> throw FSException.AlreadyExists()

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

private const val DELAY_IN_SECONDS = 10L

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

    val timeout = async { delay(DELAY_IN_SECONDS, TimeUnit.SECONDS) }

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

        is RPCException -> throw ex

        else -> {
            fsLog.warn("Unknown FS exception!")
            fsLog.warn(ex.stackTraceToString())
            Pair(CommonErrorMessage("Internal server error"), HttpStatusCode.InternalServerError)
        }
    }
}

val fsLog = LoggerFactory.getLogger("dk.sdu.cloud.storage.services.FileSystemServiceKt")!!
