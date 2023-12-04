package dk.sdu.cloud

import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiOwnedBy
import dk.sdu.cloud.calls.UCloudApiStable
import kotlinx.serialization.Serializable

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc(
    """
        A generic error message

        UCloud uses HTTP status code for all error messages. In addition and if possible, UCloud will include a message
        using a common format. Note that this is not guaranteed to be included in case of a failure somewhere else in
        the network stack. For example, UCloud's load balancer might not be able to contact the backend at all. In
        such a case UCloud will _not_ include a more detailed error message.
    """
)
data class CommonErrorMessage(
    @UCloudApiDoc("Human readable description of why the error occurred. This value is generally not stable.")
    val why: String,
    @UCloudApiDoc(
        "Machine readable description of why the error occurred. This value is stable and can be relied upon."
    )
    val errorCode: String? = null
)

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc("A request message to find a resource by a unique identifier.")
data class FindByStringId(override val id: String) : WithStringId

fun BulkResponse<FindByStringId?>.singleIdOrNull(): String? = responses.singleOrNull()?.id
fun Collection<*>.asFindByIdResponse(): BulkResponse<FindByStringId> =
    BulkResponse(map { FindByStringId(it.toString()) })
fun Collection<*>.asFindByIdResponseOptional(): BulkResponse<FindByStringId?> =
    BulkResponse(map { if (it != null) FindByStringId(it.toString()) else null })

interface WithStringId {
    val id: String
}

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc("A request message to find a resource by a numeric unique identifier.")
data class FindByLongId(val id: Long)

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc("A request message to find a resource by a numeric unique identifier.")
data class FindByIntId(val id: Int)

@Serializable
@UCloudApiOwnedBy(CoreTypes::class)
@UCloudApiStable
@UCloudApiDoc("A request message to find a resource by a numeric unique identifier.")
data class FindByDoubleId(val id: Double)
