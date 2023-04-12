package dk.sdu.cloud.debug

import kotlinx.serialization.json.JsonElement

interface DebugSensitive {
    fun removeSensitiveInformation(): JsonElement
}