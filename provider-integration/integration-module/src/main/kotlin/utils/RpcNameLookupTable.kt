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
    val incomingCall = call.fullName
    return when  {
        // NOTE(Dan): Allow streamingSearch to also trigger normal search as these are similar to eachother and gives
        // the core a bit of flexibility.
        incomingCall == "files.provider.$providerId.streamingSearch" -> {
            intent.call == "files.streamingSearch" || intent.call == "files.search"
        }

        // NOTE(Dan): SSH synchronization can be triggered by multiple user calls, yet they all result in the same
        // call on our end.
        incomingCall.startsWith("ssh_keys.") -> {
            intent.call.startsWith("ssh_keys.")
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
