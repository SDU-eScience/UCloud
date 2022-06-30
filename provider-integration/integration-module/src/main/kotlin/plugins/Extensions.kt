package dk.sdu.cloud.plugins

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.createTemporaryFile
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.fileDelete
import dk.sdu.cloud.utils.writeText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

class TypedExtension<Request, Response>(
    val requestType: KSerializer<Request>,
    val responseType: KSerializer<Response>
) {
    suspend fun invoke(extensionPath: String, request: Request): Response {
        val (tempFilePath, tempFile) = createTemporaryFile(suffix = ".json")
        tempFile.writeText(defaultMapper.encodeToString(requestType, request), autoClose = true)

        val response = executeCommandToText(
            executable = extensionPath,
            additionalDebug = defaultMapper.encodeToJsonElement(requestType, request),
            block = { addArg(tempFilePath) }
        )

        fileDelete(tempFilePath)

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

inline fun <reified Request, reified Response> extension(): TypedExtension<Request, Response> =
    TypedExtension(serializer(), serializer())

class TypedExtensionWithExecutable<Request, Response>(
    val extension: TypedExtension<Request, Response>,
    val executable: String,
)

inline fun <reified Req, reified Resp> extension(executable: String): TypedExtensionWithExecutable<Req, Resp> =
    TypedExtensionWithExecutable(extension(), executable)

inline fun <reified Req, reified Resp> optionalExtension(executable: String?): TypedExtensionWithExecutable<Req, Resp>? =
    if (executable == null) null
    else TypedExtensionWithExecutable(extension(), executable)

suspend fun <Req, Resp> TypedExtensionWithExecutable<Req, Resp>.invoke(request: Req): Resp =
    extension.invoke(executable, request)

suspend fun <Req, Resp> TypedExtensionWithExecutable<Req, Resp>?.optionalInvoke(request: Req): Resp? =
    this?.invoke(request)

class ExtensionException(message: String) : RuntimeException(message)
