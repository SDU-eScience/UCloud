package dk.sdu.cloud.app.orchestrator.api

import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.ParsedApplicationParameter
import java.util.*

class VerifiedJobInput(
    val backingData: Map<String, ParsedApplicationParameter?>
) {
    operator fun get(name: String): Any? {
        return backingData[name]
    }

    operator fun <V : ParsedApplicationParameter> get(field: ApplicationParameter<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return backingData[field.name] as? V
    }

    fun asMap(): Map<String, ParsedApplicationParameter?> = Collections.unmodifiableMap(backingData)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VerifiedJobInput

        if (backingData != other.backingData) return false

        return true
    }

    override fun hashCode(): Int {
        return backingData.hashCode()
    }
}
