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
 * [CallDescriptionContainer] for a single sub-resource.
 *
 * @param namespace The namespace of this resource. Should not include "accounting". Valid example: "compute".
 * @param resourceType The resource type that this interface implements. Valid example: "timeUsed".
 */
abstract class AbstractAccountingResourceDescriptions(
    namespace: String,
    resourceType: String
) : CallDescriptionContainer("$ACCOUNTING_NAMESPACE.$namespace.$resourceType") {
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
