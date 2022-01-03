package dk.sdu.cloud.calls

import kotlin.reflect.KProperty

actual fun CallDescriptionContainer.docCallRef(
    call: KProperty<CallDescription<*, *, *>>,
    qualified: Boolean?,
): String = call.name

actual fun visualizeValue(value: Any?): DocVisualization {
    error("Not implemented for linux target")
}
