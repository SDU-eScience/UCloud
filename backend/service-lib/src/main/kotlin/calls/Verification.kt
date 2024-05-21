package dk.sdu.cloud.calls

import kotlin.reflect.KProperty

private val noSpecialCharsRegex = Regex("[a-zA-Z0-9 æøåÆØÅ_-]+")

fun checkNoSpecialCharacters(
    property: KProperty<*>,
    value: String
) {
    checkNoSpecialCharacters(property.name, value)
}

fun checkNoSpecialCharacters(
    propertyName: String,
    value: String
) {
    if (!value.matches(noSpecialCharsRegex)) {
        throw RPCException("${propertyName} cannot contain special characters!", HttpStatusCode.BadRequest)
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
    checkSingleLine(property.name, value, allowBlanks, minimumSize, maximumSize, allowSpecial)
}

fun checkSingleLine(
    propertyName: String,
    value: String,
    allowBlanks: Boolean = false,
    minimumSize: Int = 1,
    maximumSize: Int = 1024 * 16,
    allowSpecial: Boolean = true,
) {
    if (value.contains("\n")) throw RPCException("${propertyName} cannot contain new-lines", HttpStatusCode.BadRequest)
    if (!allowBlanks && value.isBlank()) throw RPCException("${propertyName} cannot be blank", HttpStatusCode.BadRequest)
    if (value.length < minimumSize) throw RPCException("${propertyName} is too short (${value.length} < ${minimumSize})", HttpStatusCode.BadRequest)
    if (value.length > maximumSize) throw RPCException("${propertyName} is too long (${value.length} > ${maximumSize})", HttpStatusCode.BadRequest)
    if (!allowSpecial) checkNoSpecialCharacters(propertyName, value)
}

fun checkLooksLikeEmail(propertyName: String, email: String?) {
    if (email == null) return
    checkSingleLine(propertyName, email, minimumSize = 5, allowSpecial = true)
    if (!(email.contains("@") && email.substringAfter('@').contains('.'))) {
        throw RPCException("$propertyName does not look like a valid email address!", HttpStatusCode.BadRequest)
    }
}


fun checkTextLength(
    property: KProperty<*>,
    value: String,
    minimumSize: Int = 1,
    maximumSize: Int = 1024 * 16
) {
    if (value.length < minimumSize) throw RPCException("${property.name} is too short", HttpStatusCode.BadRequest)
    if (value.length > maximumSize) throw RPCException("${property.name} is too long", HttpStatusCode.BadRequest)
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
