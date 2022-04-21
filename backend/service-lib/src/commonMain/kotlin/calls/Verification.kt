package dk.sdu.cloud.calls

import kotlin.reflect.KProperty

private val noSpecialCharsRegex = Regex("[a-zA-Z0-9 æøåÆØÅ_-]+")

fun checkNoSpecialCharacters(
    property: KProperty<*>,
    value: String
) {
    if (!value.matches(noSpecialCharsRegex)) {
        throw RPCException("${property.name} cannot contain special characters!", HttpStatusCode.BadRequest)
    }
}

fun checkSingleLine(
    property: KProperty<*>,
    value: String,
    allowBlanks: Boolean = false,
    minimumSize: Int = 1,
    maximumSize: Int = 1024 * 16,
    allowSpecial: Boolean = true,
) {
    if (value.contains("\n")) throw RPCException("${property.name} cannot contain new-lines", HttpStatusCode.BadRequest)
    if (!allowBlanks && value.isBlank()) throw RPCException("${property.name} cannot be blank", HttpStatusCode.BadRequest)
    if (value.length < minimumSize) throw RPCException("${property.name} is too short", HttpStatusCode.BadRequest)
    if (value.length > maximumSize) throw RPCException("${property.name} is too long", HttpStatusCode.BadRequest)
    if (!allowSpecial) checkNoSpecialCharacters(property, value)
}

fun checkNotBlank(property: KProperty<*>, value: String) {
    if (value.isBlank()) {
        throw RPCException("${property.name} cannot be blank", HttpStatusCode.BadRequest)
    }
}

fun checkMinimumValue(property: KProperty<*>, value: Int, minimumValue: Int) {
    if (value < minimumValue) {
        throw RPCException("${property.name} must be at least $minimumValue", HttpStatusCode.BadRequest)
    }
}

fun checkMinimumValue(property: KProperty<*>, value: Long, minimumValue: Long) {
    if (value < minimumValue) {
        throw RPCException("${property.name} must be at least $minimumValue", HttpStatusCode.BadRequest)
    }
}

fun checkMaximumValue(property: KProperty<*>, value: Long, maximumValue: Long) {
    if (value > maximumValue) {
        throw RPCException("${property.name} cannot exceed $maximumValue", HttpStatusCode.BadRequest)
    }
}

fun checkMaximumValue(property: KProperty<*>, value: Int, maximumValue: Int) {
    if (value > maximumValue) {
        throw RPCException("${property.name} cannot exceed $maximumValue", HttpStatusCode.BadRequest)
    }
}

fun checkNumberInRange(property: KProperty<*>, value: Int, range: IntRange) {
    if (value !in range) {
        throw RPCException("${property.name} has an invalid value. Must be in $range", HttpStatusCode.BadRequest)
    }
}
