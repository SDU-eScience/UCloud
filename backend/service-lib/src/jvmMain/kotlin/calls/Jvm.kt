package dk.sdu.cloud.calls

import io.ktor.application.ApplicationCall
import org.intellij.lang.annotations.Language
import java.util.*
import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

actual typealias Language = Language

actual fun CallDescriptionContainer.docCallRef(
    call: KProperty<CallDescription<*, *, *>>,
    qualified: Boolean?,
): String {
    val namespace = if (call is CallableReference) {
        runCatching {
            ((call.owner as KClass<*>).objectInstance as CallDescriptionContainer).namespace
        }.getOrDefault(this.namespace)
    } else {
        this.namespace
    }

    val isQualified = qualified ?: (this.namespace != namespace)
    return if (isQualified) "[`${namespace}.${call.name}`](#operation/${namespace}.${call.name})"
        else "[`${call.name}`](#operation/${namespace}.${call.name})"
}
