package dk.sdu.cloud.utils

import dk.sdu.cloud.IntentToCall
import dk.sdu.cloud.calls.CallDescription

fun doesCallRequireSignature(providerId: String, incomingCall: String): Boolean {
    return when (incomingCall) {
        // TODO(Dan): Not entirely complete but will probably cover most if not all calls
        "file.$providerId.download.download" -> false
        else -> true
    }
}

fun doesIntentMatchCall(providerId: String, intent: IntentToCall, call: CallDescription<*, *, *>): Boolean {
    return when (val incomingCall = call.fullName) {
        "files.provider.$providerId.streamingSearch" -> {
            intent.call == "files.provider.$providerId.streamingSearch" ||
                    intent.call == "files.provider.$providerId.search"
        }

        else -> {
            // TODO(Dan): Not entirely complete but will probably cover most if not all calls
            val mappedCall = if (incomingCall.contains(".provider.")) {
                val prefix = incomingCall.substringBefore(".provider.")
                val suffix = incomingCall.substringAfterLast('.')
                "$prefix.$suffix"
            } else {
                throw IllegalStateException("Could not map '$incomingCall' to a user API equivalent call")
            }

            intent.call == mappedCall
        }
    }
}
