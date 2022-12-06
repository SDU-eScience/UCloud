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
data class ExtensionLog(
    val timestamp: Long,
    val request: String,
    val extensionPath: String,
    val stdout: String,
    val stderr: String,
    val statusCode: Int,
    val success: Boolean,

    val uid: Int = -1,
    val id: String = "",
)

@Serializable
data class ExtensionLogFlags(
    val filterFailure: Boolean? = null,
    val filterExtension: String? = null,
    val filterBeforeRelative: String? = null,
    val filterAfterRelative: String? = null,
    val query: String? = null,
)

object ExtensionLogIpc : IpcContainer("extension_log") {
    val create = createHandler(ExtensionLog.serializer(), Unit.serializer())
    val retrieve = retrieveHandler(FindByStringId.serializer(), ExtensionLog.serializer())
    val browse = browseHandler(ExtensionLogFlags.serializer(), PageV2.serializer(ExtensionLog.serializer()))
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

        var success = false
        return try {
            defaultMapper.decodeFromString(responseType, response.stdout).also {
                success = true
            }
        } catch (ex: Throwable) {
            success = false
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
        } finally {
            runCatching {
                context.ipcClient.sendRequest(
                    ExtensionLogIpc.create,
                    ExtensionLog(
                        Time.now(),
                        requestText,
                        extensionPath,
                        response.stdout,
                        response.stderr,
                        response.statusCode,
                        success
                    )
                )
            }
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

suspend fun <Req, Resp> TypedExtensionWithExecutable<Req, Resp>?.optionalInvoke(
    context: PluginContext,
    request: Req
): Resp? =
    this?.invoke(context, request)

suspend fun <Req, Resp> TypedExtension<Req, Resp>.optionalInvoke(context: PluginContext, exe: String?, req: Req): Resp? =
    if (exe != null) this.invoke(context, exe, req) else null

class ExtensionException(message: String) : RuntimeException(message)
