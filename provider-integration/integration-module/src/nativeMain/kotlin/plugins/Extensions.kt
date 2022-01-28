package dk.sdu.cloud.plugins

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.createTemporaryFile
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.writeText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import platform.posix.unlink

class TypedExtension<Request, Response>(
    val requestType: KSerializer<Request>,
    val responseType: KSerializer<Response>
) {
    fun invoke(extensionPath: String, request: Request): Response {
        val (tempFilePath, tempFile) = createTemporaryFile(suffix = ".json")
        tempFile.writeText(defaultMapper.encodeToString(requestType, request), autoClose = true)

        val response = executeCommandToText(extensionPath) {
            addArg(tempFilePath.path)
        }

        unlink(tempFilePath.path)

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

class ExtensionException(message: String) : RuntimeException(message)
