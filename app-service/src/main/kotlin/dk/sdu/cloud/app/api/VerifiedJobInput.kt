package dk.sdu.cloud.app.api

import java.util.*

class VerifiedJobInput(
    private val backingData: Map<String, Any?>
) {
    operator fun get(name: String): Any? {
        return backingData[name]
    }

    operator fun <V : Any> get(field: ApplicationParameter<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return backingData[field.name] as? V
    }

    fun asMap(): Map<String, Any?> = Collections.unmodifiableMap(backingData)
}
