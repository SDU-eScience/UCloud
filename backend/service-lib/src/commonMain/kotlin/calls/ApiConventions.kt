@file:Suppress("unused", "UNUSED_VARIABLE")

package dk.sdu.cloud.calls

import dk.sdu.cloud.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.serializer

object UCloudApi {
    const val RETRIEVE = "retrieve"
    const val BROWSE = "browse"
    const val SEARCH = "search"
    const val VERIFY = "verify"
}

/**
 * Used for RPCs which request the creation of one or more resources in UCloud.
 *
 * RPCs in this category must accept request payloads in the form of a
 * [bulk request](/backend/service-common/wiki/api_conventions.md#bulk-request).
 *
 * Calls in this category should respond back with a list of newly created IDs for every resource that has been
 * created. A client can choose to use these to display information about the newly created resources.
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `POST`
 * - Path: `${baseContext}`
 *
 * The request payload will be read, fully, from the HTTP request body.
 *
 * ---
 *
 * __⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
 * that either the entire request succeeds, or the entire request fails.
 *
 * ---
 *
 * @example [httpCreateExample]
 */
inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpCreate(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Post
        path { using(baseContext) }
        body { bindEntireRequestFromBody() }
    }
}

private fun CallDescriptionContainer.httpCreateExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    // NOTE: We use a separate data model which only contains the model specification (i.e. without additional
    // metadata about the resource, such as unique id)
    data class MyResourceSpecification(val number: Int)
    data class ResourcesCreateResponse(val ids: List<String>)

    val create = call<BulkRequest<MyResourceSpecification>, ResourcesCreateResponse, CommonErrorMessage>("create") {
        httpCreate(baseContext)
    }
}

/**
 * Used for RPCs which requests a set of resources from UCloud defined by some criteria.
 *
 * Browse RPCs are typically used for pagination of a resource, defined by some criteria. You can read more about
 * pagination [here](/backend/service-common/wiki/api_conventions.md#pagination).
 *
 * All data returned by this API must be returned in a predictable and deterministic way. In particular, this means
 * that implementors need to take care and implement a _consistent sort order_ of the items. This is unlike the
 * [httpSearch] endpoints which are allowed to sort the items by any criteria. Results from a search RPC is suitable
 * only for human consumption while results from a browse RPC are suitable for human and machine consumption.
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `GET`
 * - Path: `${baseContext}/browse`
 *
 * The entire request payload will be bound to the query parameters of the request.
 *
 * @example [httpBrowseExample]
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified R : Any> CallDescription<R, *, *>.httpBrowse(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ
        this.roles = roles
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
            +UCloudApi.BROWSE
        }

        if (R::class != Unit::class) {
            params {
                serializer<R>().descriptor.elementDescriptors.forEach {
                    +boundTo(it.serialName)
                }
            }
        }
    }
}

private fun CallDescriptionContainer.httpBrowseExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    run {
        // Browse via the pagination v2 API
        data class ResourcesBrowseRequest(
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val browse = call<ResourcesBrowseRequest, PageV2<MyResource>, CommonErrorMessage>("browse") {
            httpBrowse(baseContext)
        }
    }
}

/**
 * Used for RPCs which indicate a request for a specific resource from UCloud.
 *
 * Calls using this category must fetch a _single_ resource. This resource is normally fetched by its ID but it may
 * also be retrieved by some other uniquely identifying piece of information. If more than one type of identifier is
 * supported then the call must reject requests (with `400 Bad Request`) which does not include exactly one identifier.
 *
 * Results for this call type should always be deterministic. If the resource cannot be found then calls must return
 * `404 Not Found`.
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `GET`
 * - Path (no sub-resource): `${baseContext}/retrieve`
 * - Path (with sub-resource): `${baseContext}/retrieve${subResource}`
 *
 * The entire request payload will be bound to the query parameters of the request.
 *
 * @example [httpRetrieveExample]
 */
@OptIn(ExperimentalSerializationApi::class)
inline fun <reified R : Any> CallDescription<R, *, *>.httpRetrieve(
    baseContext: String,
    subResource: String? = null,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ
        this.roles = roles
    }

    http {
        method = HttpMethod.Get

        path {
            using(baseContext)
            +"${UCloudApi.RETRIEVE}${subResource?.capitalize() ?: ""}"
        }

        if (R::class != Unit::class) {
            params {
                serializer<R>().descriptor.elementDescriptors.forEach {
                    +boundTo(it.serialName)
                }
            }
        }
    }
}

private fun CallDescriptionContainer.httpRetrieveExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    run {
        // Retrieve by unique identifier
        data class ResourcesRetrieveRequest(val id: String)

        val retrieve = call<ResourcesRetrieveRequest, MyResource, CommonErrorMessage>("retrieve") {
            httpRetrieve(baseContext)
        }
    }

    run {
        // Retrieve by one or more unique parameters
        data class ResourcesRetrieveRequest(
            val id: String? = null, // optional parameters should have a default value in Kotlin code
            val number: Int? = null
        ) {
            init {
                // In this case `number` also uniquely identifies the resource. The code must reject requests
                // that do not include exactly one query criteria.

                if ((id == null && number == null) || (id != null && number != null)) {
                    throw RPCException("Request must unique include either 'id' or 'number'", HttpStatusCode.BadRequest)
                }
            }
        }

        val retrieve = call<ResourcesRetrieveRequest, MyResource, CommonErrorMessage>("retrieve") {
            httpRetrieve(baseContext)
        }
    }
}

/**
 * Used for RPCs which request a set of resources from UCloud defined by some search criteria.
 *
 * Search RPCs are typically used for calls which allows a human to look for their data. This means that there
 * are no strict requirements for consistent or deterministic results.
 *
 * If a machine needs to consume a set of resources in a predictable fashion then it should prefer RPCs from the
 * [httpBrowse] category.
 *
 * This type of call typically exposes its results via the
 * [pagination](/backend/service-common/wiki/api_conventions.md#pagination) API.
 *
 * Search criteria, unlike browse criterion, can also with great benefit choose to search in multiple fields at the
 * same time. For example, if a user is searching for a file with a simple free-text query. Then it would be beneficial
 * for the server to search for this criteria in multiple locations, such as the file name and contents.
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `POST`
 * - Path: `${baseContext}/search`
 *
 * The request payload will be read, fully, from the HTTP request body.
 *
 * ---
 *
 * __📝 NOTE:__ This call category uses `POST` instead of `GET`. `POST` is used because of the more relaxed requirements
 * for deterministic results which makes search results a lot less desirable to cache, for example. This also makes
 * search calls more suitable for large(er) criterion.
 *
 * ---
 *
 * @example [httpSearchExample]
 */
inline fun <reified R : Any> CallDescription<R, *, *>.httpSearch(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ
        this.roles = roles
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +UCloudApi.SEARCH
        }

        body { bindEntireRequestFromBody() }
    }
}

private fun CallDescriptionContainer.httpSearchExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    run {
        // Search via the pagination v2 API
        data class ResourcesSearchRequest(
            // Note: if next != null then the service is allowed to assume that your search criteria hasn't changed
            // since last query.
            val query: String,
            override val itemsPerPage: Int? = null,
            override val next: String? = null,
            override val consistency: PaginationRequestV2Consistency? = null,
            override val itemsToSkip: Long? = null,
        ) : WithPaginationRequestV2

        val search = call<ResourcesSearchRequest, PageV2<MyResource>, CommonErrorMessage>("search") {
            httpSearch(baseContext)
        }
    }
}

/**
 * _Interacts_ with a UCloud resource, typically causing an update to the resource itself.
 *
 * RPCs in this category must accept request payloads in the form of a
 * [bulk request](/backend/service-common/wiki/api_conventions.md#bulk-request).
 *
 * This category of calls can be used for any type of interaction with a UCloud resource. Many resources in UCloud
 * are represented by some underlying complex logic. As a result, it is typically not meaningful or possible to simple
 * patch the resource's data model directly. Instead, clients must _interact_ with the resources to change them.
 *
 * For example, if a client wishes to suspend a virtual machine, they would not send back a new representation of the
 * virtual machine stating that it is suspended. Instead, clients should send a `suspend` interaction. This instructs
 * the backend services to correctly initiate suspend procedures.
 *
 * All update operations have a name associated with them. Names should clearly indicate which underlying procedure
 * will be triggered by the RPC. For example, suspending a virtual machine would likely be called `suspend`.
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `POST` (_always_)
 * - Path: `${baseContext}/${op}` where `op` is the name of the operation, e.g. `suspend`
 *
 * The request payload will be read, fully, from the HTTP request body.
 *
 * ---
 *
 * __⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
 * that either the entire request succeeds, or the entire request fails.
*
 * ---
 *
 * @example [httpUpdateExample]
 */
inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpUpdate(
    baseContext: String,
    operation: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +operation
        }

        body { bindEntireRequestFromBody() }
    }
}

private fun CallDescriptionContainer.httpUpdateExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    data class ResourcesIncrementRequestItem(val incrementStep: Int = 1)
    data class ResourcesCreateResponse(val ids: List<String>)

    val increment = call<BulkRequest<ResourcesIncrementRequestItem>, ResourcesCreateResponse, CommonErrorMessage>(
        "increment"
    ) {
        httpUpdate(baseContext, "increment")
    }
}

/**
 * Used for RPCs which request the deletion of one or more resources in UCloud.
 *
 * RPCs in this category must accept request payloads in the form of a
 * [bulk request](/backend/service-common/wiki/api_conventions.md#bulk-request).
 *
 * Calls in this category should choose to accept as little information about the resources, while still uniquely
 * identifying them.
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `DELETE`
 * - Path: `${baseContext}`
 *
 * The request payload will be read, fully, from the HTTP request body.
 *
 * ---
 *
 * __⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
 * that either the entire request succeeds, or the entire request fails.
 *
 * ---
 *
 * @example [httpDeleteExample]
 */
inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpDelete(
    baseContext: String,
    roles: Set<Role> = Roles.END_USER,
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Delete

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }
}

private fun CallDescriptionContainer.httpDeleteExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    val delete = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }
}

/**
 * Category used for calls which need to verify state between two decentralized services.
 *
 * This category can allows the services to make sure that their state are consistent between two versions which have
 * different ideas of the state. This can happen between, for example: service <=> service or service <=> provider.
 * This category is typically found in the provider APIs and are periodically invoked by the UCloud side.
 *
 * Verify calls do not have to send the entire database in a single call. Instead it should generally choose to send
 * only a small snapshot of the database. This allows both sides to process the request in a reasonable amount of time.
 * The recipient of this request should notify the other end through other means if these are not in sync (a call is
 * typically provided for this).
 *
 * On HTTP this will apply the following routing logic:
 *
 * - Method: `POST`
 * - Path (no sub-resource): `${baseContext}/verify`
 * - Path (with sub-resource): `${baseContext}/verfiy${subResource}`
 *
 * The request payload will be read from the HTTP request body.
 *
 * @example [httpVerifyExample]
 */
inline fun <reified R : Any> CallDescription<BulkRequest<R>, *, *>.httpVerify(
    baseContext: String,
    informationToVerify: String? = null,
    roles: Set<Role> = Roles.PRIVILEGED,
) {
    auth {
        access = AccessRight.READ_WRITE
        this.roles = roles
    }

    http {
        method = HttpMethod.Post

        path {
            using(baseContext)
            +"${UCloudApi.VERIFY}${informationToVerify?.capitalize() ?: ""}"
        }

        body { bindEntireRequestFromBody() }
    }
}

private fun CallDescriptionContainer.httpVerifyExample() {
    data class MyResource(val id: String, val number: Int)
    val baseContext = "/api/myresources"

    // Note: We send the entire resource in the request to make sure both sides have enough information to verify
    // that state is in-sync.
    val verify = call<BulkRequest<MyResource>, Unit, CommonErrorMessage>("verify") {
        httpVerify(baseContext)
    }
}

@TSDefinition("""
export type BulkRequest<T> = { type: "bulk", items: T[] }
""")
@UCloudApiDoc("""A base type for requesting a bulk operation.
    
---

__⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
that either the entire request succeeds, or the entire request fails.

There are two exceptions to this rule:

1. Certain calls may choose to only guarantee this at the provider level. That is if a single call contain request
for multiple providers, then in rare occasions (i.e. crash) changes might not be rolled back immediately on all
providers. A service _MUST_ attempt to rollback already committed changes at other providers.

2. The underlying system does not provide such guarantees. In this case the service/provider _MUST_ support the
verification API to cleanup these resources later.

---
    

""")
@Serializable
data class BulkRequest<out T : Any>(val items: List<T>)

fun <T : Any> bulkRequestOf(vararg items: T): BulkRequest<T> {
    if (items.isEmpty()) error("No items provided")
    return BulkRequest(listOf(*items))
}

fun <T : Any> bulkRequestOf(items: Collection<T>): BulkRequest<T> {
    if (items.isEmpty()) error("No items provided")
    return BulkRequest(items.toList())
}

@Serializable
data class BulkResponse<T>(val responses: List<T>)