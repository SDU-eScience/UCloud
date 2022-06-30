package dk.sdu.cloud.utils

fun mapProviderApiToUserApi(incomingCall: String): String {
    // TODO(Dan): Not entirely complete but will probably cover most if not all calls
    when(incomingCall) {
        // Custom entries
    }

    if (incomingCall.contains(".provider.")) {
        val prefix = incomingCall.substringBefore(".provider.")
        val suffix = incomingCall.substringAfterLast('.')
        return "$prefix.$suffix"
    }

    throw IllegalStateException("Could not map '$incomingCall' to a user API equivalent call")
}