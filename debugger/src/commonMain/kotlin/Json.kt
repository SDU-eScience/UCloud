package dk.sdu.cluod.debug

import kotlinx.serialization.json.Json

val defaultMapper = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
