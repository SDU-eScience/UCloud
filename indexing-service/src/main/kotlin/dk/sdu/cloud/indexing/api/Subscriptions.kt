package dk.sdu.cloud.indexing.api

import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.events.EventStream
import dk.sdu.cloud.events.JsonEventStream
import dk.sdu.cloud.file.api.StorageEvent
import io.ktor.http.HttpMethod

data class AddSubscriptionRequest(val fileIds: Set<String>)
typealias AddSubscriptionResponse = Unit

data class RemoveSubscriptionRequest(val fileIds: Set<String>)
typealias RemoveSubscriptionResponse = Unit

object Subscriptions : CallDescriptionContainer("indexing.subscriptions") {
    private const val baseContext = "/api/indexing/subscriptions"

    val addSubscription = call<AddSubscriptionRequest, AddSubscriptionResponse, CommonErrorMessage>("addSubscription") {
        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ_WRITE
        }

        http {
            method = HttpMethod.Put

            path {
                using(baseContext)
            }

            body { bindEntireRequestFromBody() }
        }
    }

    val removeSubscription =
        call<RemoveSubscriptionRequest, RemoveSubscriptionResponse, CommonErrorMessage>("removeSubscription") {
            auth {
                roles = Roles.PRIVILEDGED
                access = AccessRight.READ_WRITE
            }

            http {
                method = HttpMethod.Delete

                path {
                    using(baseContext)
                }

                body { bindEntireRequestFromBody() }
            }
        }
}

/**
 * Retrieves a reference to the stream used to publish file subscription data.
 *
 * The [name] should match the name of the service. This should not include the leading '_' although this is
 * automatically removed.
 *
 * @see [Subscriptions.addSubscription]
 * @see [Subscriptions.removeSubscription]
 */
fun subscriptionStream(name: String): EventStream<StorageEvent> =
    JsonEventStream<StorageEvent>(
        name.removePrefix("_") + "-file-sub",
        jacksonTypeRef(),
        { it.file?.fileIdOrNull ?: "" }
    )
