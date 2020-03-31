package dk.sdu.cloud.accounting.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.calls.CallDescriptionContainer
import dk.sdu.cloud.calls.auth
import dk.sdu.cloud.calls.bindEntireRequestFromBody
import dk.sdu.cloud.calls.call
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

typealias UsageRequest = Unit

data class UsageResponse(
    val usage: Long,
    val quota: Long? = null,
    val dataType: String? = null,
    val title: String? = null
)

internal const val ACCOUNTING_NAMESPACE = "accounting"

/**
 * An accounting event. Contains basic information that is required.
 *
 * Implementations are encouraged to add any other relevant data as well. This is only the absolute minimum to help
 * enforce a unified API.
 */
interface AccountingEvent {
    /**
     * A title for the event. This should contain a brief description of the type of event.
     *
     * Example: "Job Completed"
     */
    val title: String

    /**
     * A description of the event.
     *
     * If included this hints to the UI that it should display this description along with the event.
     *
     * Example: "Application 'foo@1.0.0' used 42:10:30 across 5 nodes."
     */
    val description: String? get() = null

    /**
     * Unix timestamp in milliseconds of when the event occurred
     */
    val timestamp: Long
}

/**
 * [CallDescriptionContainer] for a single sub-resource.
 *
 * @param namespace The namespace of this resource. Should not include "accounting". Valid example: "compute".
 * @param resourceType The resource type that this interface implements. Valid example: "timeUsed".
 */
abstract class AbstractAccountingResourceDescriptions<Event : AccountingEvent>(
    namespace: String,
    val resourceType: String
) : CallDescriptionContainer("$ACCOUNTING_NAMESPACE.$namespace.$resourceType") {
    // TODO This stuff does not work well for auditing.

    val baseContext: String = "/api/$ACCOUNTING_NAMESPACE/$namespace/$resourceType"

    /**
     * Returns usage for this resource for a given time period.
     */
    val usage = call<UsageRequest, UsageResponse, CommonErrorMessage>("usage") {
        auth {
            access = AccessRight.READ
        }

        http {
            method = HttpMethod.Get

            path {
                using(baseContext)
                +"usage"
            }
        }
    }
}
