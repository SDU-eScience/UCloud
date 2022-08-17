package dk.sdu.cloud.utils

const val CALL_DOES_NOT_REQUIRE_SIGNED_INTENT = "....NO_SIGNED_INTENT_REQUIRED...."

fun mapProviderApiToUserApi(providerId: String, incomingCall: String): String {
    // TODO(Dan): Not entirely complete but will probably cover most if not all calls
    when(incomingCall) {
        "file.$providerId.download.download" -> return CALL_DOES_NOT_REQUIRE_SIGNED_INTENT
    }

    if (incomingCall.contains(".provider.")) {
        val prefix = incomingCall.substringBefore(".provider.")
        val suffix = incomingCall.substringAfterLast('.')
        return "$prefix.$suffix"
    }

    throw IllegalStateException("Could not map '$incomingCall' to a user API equivalent call")
}