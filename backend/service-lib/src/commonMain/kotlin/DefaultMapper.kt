package dk.sdu.cloud

import kotlinx.serialization.json.Json
import kotlin.native.concurrent.SharedImmutable

@SharedImmutable
val defaultMapper = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
    isLenient = true
    coerceInputValues = true
}.freeze()

expect fun <T> T.freeze(): T
