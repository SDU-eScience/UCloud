package dk.sdu.cloud

import dk.sdu.cloud.debug.*
import kotlinx.serialization.Serializable

@Serializable
data class ReadableStackTrace(
    val type: String,
    val message: String,
    val frames: List<String>,
) {
    override fun toString(): String = buildString {
        appendLine("$type: $message")
        for (frame in frames) {
            appendLine("  at $frame")
        }
    }
}

data class ProposedStackFrame(val className: String, val methodName: String)

fun makeStackFrameReadable(className: String, methodName: String): ProposedStackFrame? {
    if (className.startsWith("kotlin.coroutines.native")) return null
    if (className.startsWith("kotlinx.coroutines")) return null
    if (className.startsWith("kotlinx.serialization") && className.contains(".internal.")) return null
    if (className.startsWith("io.ktor.features.CallLogging")) return null
    if (className.startsWith("io.ktor.util.pipeline")) return null
    if (className.startsWith("io.ktor.server.engine")) return null
    if (className.startsWith("io.ktor.server.netty")) return null
    if (className.startsWith("io.ktor.routing.")) return null
    if (className.startsWith("io.netty.util.")) return null
    if (className.startsWith("io.netty.channel.")) return null
    if (className.startsWith("java.lang.Thread")) return null
    if (className.startsWith("dk.sdu.cloud.calls.server.IngoingHttpInterceptor\$addCallListenerForCall")) return null
    if (className.startsWith("kotlin.Throwable") && methodName.startsWith("<init>")) return null
    if (className.startsWith("kotlin.Exception") && methodName.startsWith("<init>")) return null
    if (className.startsWith("kotlin.RuntimeException") && methodName.startsWith("<init>")) return null
    if (className.startsWith("kotlin.coroutines.jvm")) return null

    if (methodName.contains("COROUTINE$")) return null
    if (className.contains("COROUTINE$")) return null
    if (methodName.contains("<anonymous>")) return null
    if (className.contains("<anonymous>")) return null

    if (methodName == "invoke" || (methodName == "invokeSuspend" && className.contains("$"))) {
        return ProposedStackFrame(className.substringBefore('$'), className.substringAfter('$').substringBefore('$'))
    }


    return ProposedStackFrame(className, methodName.substringBefore("__at__").substringBefore('(').substringBefore('$'))
}

suspend fun DebugSystem?.logThrowable(
    message: String,
    throwable: Throwable,
    importance: MessageImportance = MessageImportance.THIS_IS_ODD,
) {
    if (this == null) return
    log(importance, message, defaultMapper.encodeToJsonElement(ReadableStackTrace.serializer(), throwable.toReadableStacktrace()))
}
