package dk.sdu.cloud.provider.api

import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.withHooks
import io.ktor.client.request.*

fun AuthenticatedClient.withProxyInfo(username: String?, signedIntent: String?): AuthenticatedClient {
    return withHooks(
        beforeHook = {
            if (username != null) {
                when (it) {
                    is OutgoingHttpCall -> {
                        it.builder.header(
                            IntegrationProvider.UCLOUD_USERNAME_HEADER,
                            base64Encode(username.encodeToByteArray())
                        )

                        if (signedIntent != null) {
                            it.builder.header(
                                IntegrationProvider.UCLOUD_SIGNED_INTENT,
                                signedIntent,
                            )
                        }
                    }

                    is OutgoingWSCall -> {
                        it.attributes[OutgoingWSCall.proxyAttribute] = username
                        if (signedIntent != null) {
                            it.attributes[OutgoingWSCall.signedIntentAttribute] = signedIntent
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Cannot attach proxy info to this client $it")
                    }
                }
            }
        }
    )
}
