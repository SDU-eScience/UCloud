package dk.sdu.cloud.plugins

import dk.sdu.cloud.FindByStringId
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ipc.IpcContainer
import dk.sdu.cloud.ipc.sendRequest
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.createTemporaryFile
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.fileDelete
import dk.sdu.cloud.utils.writeText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

@Serializable
data class ExtensionFailure(
    val timestamp: Long,
    val request: String,
    val extensionPath: String,
    val stdout: String,
    val stderr: String,
    val statusCode: Int,

    val uid: Int = -1,
    val id: String = "",
)

object ExtensionFailureIpc : IpcContainer("ipc_failure") {
    val create = createHandler(ExtensionFailure.serializer(), Unit.serializer())
    val browse = browseHandler(Unit.serializer(), PageV2.serializer(ExtensionFailure.serializer()))
    val markAsRead = updateHandler("markAsRead", BulkRequest.serializer(FindByStringId.serializer()), Unit.serializer())
    val clear = updateHandler("clear", Unit.serializer(), Unit.serializer())
}

class TypedExtension<Request, Response>(
    val requestType: KSerializer<Request>,
    val responseType: KSerializer<Response>
) {
    suspend fun invoke(context: PluginContext, extensionPath: String, request: Request): Response {
        val (tempFilePath, tempFile) = createTemporaryFile(suffix = ".json")
        val requestText = defaultMapper.encodeToString(requestType, request)
        tempFile.writeText(requestText, autoClose = true)

        val response = executeCommandToText(
            executable = extensionPath,
            additionalDebug = defaultMapper.encodeToJsonElement(requestType, request),
            block = { addArg(tempFilePath) }
        )

        fileDelete(tempFilePath)

        runCatching {
            context.ipcClient.sendRequest(
                ExtensionFailureIpc.create,
                ExtensionFailure(
                    Time.now(),
                    requestText,
                    extensionPath,
                    response.stdout,
                    response.stderr,
                    response.statusCode
                )
            )
        }

        return try {
            defaultMapper.decodeFromString(responseType, response.stdout)
        } catch (ex: Throwable) {
            throw ExtensionException(buildString {
                appendLine("Extension '$extensionPath' has failed!")
                appendLine()

                appendLine("Exit code: ${response.statusCode}")
                appendLine()


                appendLine("stdout:")
                appendLine(response.stdout.prependIndent("    "))
                appendLine()

                appendLine("stderr:")
                appendLine(response.stderr.prependIndent("    "))
                appendLine()

                appendLine("Decoding exception:")
                appendLine(ex.stackTraceToString().prependIndent("    "))
            })
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

inline fun <reified Request, reified Response> extension(
    req: KSerializer<Request>,
    res: KSerializer<Response>
): TypedExtension<Request, Response> = TypedExtension(req, res)

class TypedExtensionWithExecutable<Request, Response>(
    val extension: TypedExtension<Request, Response>,
    val executable: String,
)

inline fun <reified Req, reified Resp> extension(
    executable: String,
    req: KSerializer<Req>,
    res: KSerializer<Resp>
): TypedExtensionWithExecutable<Req, Resp> =
    TypedExtensionWithExecutable(extension(req, res), executable)

inline fun <reified Req, reified Resp> optionalExtension(
    executable: String?,
    req: KSerializer<Req>,
    res: KSerializer<Resp>
): TypedExtensionWithExecutable<Req, Resp>? =
    if (executable == null) null
    else TypedExtensionWithExecutable(extension(req, res), executable)

suspend fun <Req, Resp> TypedExtensionWithExecutable<Req, Resp>.invoke(context: PluginContext, request: Req): Resp =
    extension.invoke(context, executable, request)

suspend fun <Req, Resp> TypedExtensionWithExecutable<Req, Resp>?.optionalInvoke(context: PluginContext, request: Req): Resp? =
    this?.invoke(context, request)

class ExtensionException(message: String) : RuntimeException(message)
