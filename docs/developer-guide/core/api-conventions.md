<p align='center'>
<a href='/docs/developer-guide/core/types.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/api-stability.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / API Conventions
# API Conventions

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_In this document we cover the API conventions for UCloud along with giving guidance on how to implement them in UCloud._

## Network Protocol

Before we start our discussion of API conventions and architecture, we will first take a brief moment to discuss the
network protocols used for communication. All clients, e.g. the web-client, interact with UCloud by invoking remote
procedure calls (RPC) over the network.

UCloud implements RPCs over the network using either an HTTP backend or a WebSocket backend. By default, RPCs use the
HTTP backend. RPCs which require real-time output, or the ability to push from the server-side use the WebSocket
backend.

```
GET /api/jobs/browse?
    includeProduct=true&
    itemsPerPage=50&
    sortBy=CREATED_AT HTTP/1.1
Authorization: Bearer <TOKEN>
Project: <Project>

---

HTTP/2 200 OK
content-type: application/json; charset=UTF-8
content-length: <LENGTH>
job-id: <ID>

{
  "itemsPerPage": 50,
  "items": [
    {
      "id": "<ID>",
      "updates": [],
      "billing": {},
      "parameters": {
        "application": {
          "name": "terminal-ubuntu",
          "version": "0.8.6"
        }
      }
    }
  ],
  "next": null
}

```

__Figure 1:__ Simplified and formatted view of the HTTP backend

```
GET /api/jobs HTTP/1.1
Connection: keep-alive, Upgrade
Upgrade: websocket

C->S:
{
  "call": "jobs.follow",
  "streamId": "0",
  "payload": {
    "id": "<ID>"
  },
  "project": "<PROJECT>",
  "bearer": "<TOK>"
}

S->C:
{
  "type": "message",
  "streamId": "0",
  "payload": {
    "newStatus": {
      "state": "IN_QUEUE",
    }
  }
}

S->C:
{
  "type": "message",
  "streamId": "0",
  "payload": {
    "log": [
      {
        "rank": 0,
        "stdout": "Application is now running\n"
      }
    ],
  }
}
```

__Figure 2:__ Simplified and formatted view of the WebSocket backend

Both network backends take a simple approach to the problem while remaining familiar to most who have used other HTTP or
WebSocket based APIs. UCloud has taken a few nontraditional approaches for certain problems, we aim to only do this when
the traditional approaches have significant flaws, such as performance and reliability. We will attempt to cover the
differences later.

### Authentication

You can read more about authentication using the different network protocols [here](/docs/developer-guide/core/users/authentication/users.md).

### Data Serialization

As is visible from Figure 1 and Figure 2, UCloud uses JSON for data serialization. This format has been picked because
of its familiarity to most developers. While also being easy to read by humans and machines.

---

__📝 NOTE:__ UCloud generally ignores the `Accept` and `Content-Type` headers in request payloads. UCloud does not
perform any type of content-type negotiation. This significantly simplifies our server-side code. We do not believe
there are significant benefits in supporting multiple representation of the same data through this mechanism.

UCloud will include a valid `Content-Type` header in response payloads. This content-type will only describe the format
used to serialize the data. It will not describe the contents of the data. That is, UCloud assumes that clients are
already aware of the data that any specific RPC produces.

---

UCloud attempts to follow the principal of relaxed requirements for request payloads while following a more strict and
consistent approach for response payloads. You can read more about how we implement this in
practice [here](/docs/developer-guide/development/micro/serialization.md).

## RPC Interfaces

The API design of UCloud is centered around resources. A resource is any entity which clients can query information
about or interact with. For example, clients of UCloud can ask about the status of a compute job. Clients can also ask
UCloud to cancel an existing compute job.

All resources in UCloud have a chunk of the URL path carved out for them. This 'namespace' serves as their area and all
requests are generally routed in the same direction. For example, [avatars](/docs/developer-guide/core/users/avatars.md) uses
`/api/avatar` for all their requests. This namespace is commonly referred to as the `baseContext` inside of UCloud.

All RPCs aim to either describe the state of resources in UCloud or interact with one or more resources. RPCs of UCloud
fall into one of the following categories:

| Category | Description |
|----------|-------------|
| [`Retrieve`](#retrieve) | Requests a specific resource from UCloud. |
| [`Browse`](#browse) | Requests a set of resources from UCloud defined by some criteria. The data is returned in a predictable and deterministic way. |
| [`Search`](#search) | Requests a set of resources from UCloud defined by a search criteria. Data is ranked by an undefined criteria. Results are not predictable or deterministic. |
| [`Create`](#create) | Instructs UCloud to create one or more resources. |
| [`Delete`](#delete) | Instructs UCloud to delete one or more resources. |
| [`Update`](#update) | _Interacts_ with a UCloud resource which may cause an update to the resource's data model. |
| [`Subscribe`](#subscribe) | (WebSocket only) Subscribes for real-time updates of a resource. |
| [`Verify`](#verify) | Category used for calls which verify state between two decentralized services. |

---

__📝 NOTE:__ Resources don't have to implement one of each call category. For example, some resources cannot be created
explicitly by a client and can only be interacted with. For example, all UCloud users have exactly one
[avatar](/docs/developer-guide/core/users/avatars.md). Clients can choose to update their avatar, but they cannot create a new
avatar in the system.

---

### `Retrieve`

<!-- ktfunref:service-lib:calls.httpRetrieve:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
Used for RPCs which indicate a request for a specific resource from UCloud.

Calls using this category must fetch a _single_ resource. This resource is normally fetched by its ID but it may
also be retrieved by some other uniquely identifying piece of information. If more than one type of identifier is
supported then the call must reject requests (with `400 Bad Request`) which does not include exactly one identifier.

Results for this call type should always be deterministic. If the resource cannot be found then calls must return
`404 Not Found`.

On HTTP this will apply the following routing logic:

- Method: `GET`
- Path (no sub-resource): `${baseContext}/retrieve`
- Path (with sub-resource): `${baseContext}/retrieve${subResource}`

The entire request payload will be bound to the query parameters of the request.

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpRetrieveExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
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
            val number: Int? = null,
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
````

<!--</editor-fold>-->
<!-- /ktfunref -->

### `Browse`

<!-- ktfunref:service-lib:calls.httpBrowse:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
Used for RPCs which requests a set of resources from UCloud defined by some criteria.

Browse RPCs are typically used for pagination of a resource, defined by some criteria. 

All data returned by this API must be returned in a predictable and deterministic way. In particular, this means
that implementors need to take care and implement a _consistent sort order_ of the items. This is unlike the
[httpSearch] endpoints which are allowed to sort the items by any criteria. Results from a search RPC is suitable
only for human consumption while results from a browse RPC are suitable for human and machine consumption.

On HTTP this will apply the following routing logic:

- Method: `GET`
- Path: `${baseContext}/browse`

The entire request payload will be bound to the query parameters of the request.

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpBrowseExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
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
````

<!--</editor-fold>-->
<!-- /ktfunref -->

### `Search`

<!-- ktfunref:service-lib:calls.httpSearch:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
Used for RPCs which request a set of resources from UCloud defined by some search criteria.

Search RPCs are typically used for calls which allows a human to look for their data. This means that there
are no strict requirements for consistent or deterministic results.

If a machine needs to consume a set of resources in a predictable fashion then it should prefer RPCs from the
[httpBrowse] category.

This type of call typically exposes its results via the
pagination API.

Search criteria, unlike browse criterion, can also with great benefit choose to search in multiple fields at the
same time. For example, if a user is searching for a file with a simple free-text query. Then it would be beneficial
for the server to search for this criteria in multiple locations, such as the file name and contents.

On HTTP this will apply the following routing logic:

- Method: `POST`
- Path: `${baseContext}/search`

The request payload will be read, fully, from the HTTP request body.

---

__📝 NOTE:__ This call category uses `POST` instead of `GET`. `POST` is used because of the more relaxed requirements
for deterministic results which makes search results a lot less desirable to cache, for example. This also makes
search calls more suitable for large(er) criterion.

---

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpSearchExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
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
````

<!--</editor-fold>-->
<!-- /ktfunref -->

### `Create`

<!-- ktfunref:service-lib:calls.httpCreate:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
Used for RPCs which request the creation of one or more resources in UCloud.

RPCs in this category should accept request payloads in the form of a bulk request.

Calls in this category should respond back with a list of newly created IDs for every resource that has been
created. A client can choose to use these to display information about the newly created resources.

On HTTP this will apply the following routing logic:

- Method: `POST`
- Path: `${baseContext}`

The request payload will be read, fully, from the HTTP request body.

---

__⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
that either the entire request succeeds, or the entire request fails.

---

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpCreateExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
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
````

<!--</editor-fold>-->
<!-- /ktfunref -->

### `Delete`

<!-- ktfunref:service-lib:calls.httpDelete:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
Used for RPCs which request the deletion of one or more resources in UCloud.

RPCs in this category should accept request payloads in the form of a bulk request.

Calls in this category should choose to accept as little information about the resources, while still uniquely
identifying them.

On HTTP this will apply the following routing logic:

- Method: `DELETE`
- Path: `${baseContext}`

The request payload will be read, fully, from the HTTP request body.

---

__⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
that either the entire request succeeds, or the entire request fails.

---

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpDeleteExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
private fun CallDescriptionContainer.httpDeleteExample() {
    data class MyResource(val id: String, val number: Int)

    val baseContext = "/api/myresources"

    val delete = call<BulkRequest<FindByStringId>, Unit, CommonErrorMessage>("delete") {
        httpDelete(baseContext)
    }
}
````

<!--</editor-fold>-->
<!-- /ktfunref -->

### `Update`

<!-- ktfunref:service-lib:calls.httpUpdate:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
_Interacts_ with a UCloud resource, typically causing an update to the resource itself.

RPCs in this category should accept request payloads in the form of a bulk request.

This category of calls can be used for any type of interaction with a UCloud resource. Many resources in UCloud
are represented by some underlying complex logic. As a result, it is typically not meaningful or possible to simple
patch the resource's data model directly. Instead, clients must _interact_ with the resources to change them.

For example, if a client wishes to suspend a virtual machine, they would not send back a new representation of the
virtual machine stating that it is suspended. Instead, clients should send a `suspend` interaction. This instructs
the backend services to correctly initiate suspend procedures.

All update operations have a name associated with them. Names should clearly indicate which underlying procedure
will be triggered by the RPC. For example, suspending a virtual machine would likely be called `suspend`.

On HTTP this will apply the following routing logic:

- Method: `POST` (_always_)
- Path: `${baseContext}/${op}` where `op` is the name of the operation, e.g. `suspend`

The request payload will be read, fully, from the HTTP request body.

---

__⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction. This means
that either the entire request succeeds, or the entire request fails.

---

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpUpdateExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
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
````

<!--</editor-fold>-->
<!-- /ktfunref -->

### `Subscribe`

No documentation.

### `Verify`

<!-- ktfunref:service-lib:calls.httpVerify:includeSignature=false -->
<!--<editor-fold desc="Generated documentation">-->
Category used for calls which need to verify state between two decentralized services.

This category can allows the services to make sure that their state are consistent between two versions which have
different ideas of the state. This can happen between, for example: service <=> service or service <=> provider.
This category is typically found in the provider APIs and are periodically invoked by the UCloud side.

Verify calls do not have to send the entire database in a single call. Instead it should generally choose to send
only a small snapshot of the database. This allows both sides to process the request in a reasonable amount of time.
The recipient of this request should notify the other end through other means if these are not in sync (a call is
typically provided for this).

On HTTP this will apply the following routing logic:

- Method: `POST`
- Path (no sub-resource): `${baseContext}/verify`
- Path (with sub-resource): `${baseContext}/verfiy${subResource}`

The request payload will be read from the HTTP request body.

<!--</editor-fold>-->
<!-- /ktfunref -->

__Example usage:__

<!-- ktfunref:service-lib:calls.httpVerifyExample:includeSignature=true:includeBody=true -->
<!--<editor-fold desc="Generated documentation">-->
```kotlin
private fun CallDescriptionContainer.httpVerifyExample() {
    data class MyResource(val id: String, val number: Int)

    val baseContext = "/api/myresources"

    // Note: We send the entire resource in the request to make sure both sides have enough information to verify
    // that state is in-sync.
    val verify = call<BulkRequest<MyResource>, Unit, CommonErrorMessage>("verify") {
        httpVerify(baseContext)
    }
}
````

<!--</editor-fold>-->
<!-- /ktfunref -->

## Bulk Request

<!-- typedoc:dk.sdu.cloud.calls.BulkRequest:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
A base type for requesting a bulk operation.

| Property | Type | Description |
|----------|------|-------------|
| `items` | `Array<Any>` | No documentation |

    
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
    




<!--</editor-fold>-->
<!-- /typedoc -->

## Pagination

### `PaginationRequestV2`

<!-- typedoc:dk.sdu.cloud.PaginationRequestV2:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
The base type for requesting paginated content.

| Property | Type | Description |
|----------|------|-------------|
| `itemsPerPage` | `Int` | Requested number of items per page. Supported values: 10, 25, 50, 100, 250. |
| `next` | `String?` | A token requesting the next page of items |
| `consistency` | `("PREFER" or "REQUIRE")?` | Controls the consistency guarantees provided by the backend |
| `itemsToSkip` | `Long?` | Items to skip ahead |


Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
semantics of the call:

| Consistency | Description |
|-------------|-------------|
| `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
| `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |

The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
results to be consistent if it contains a complete view at some point in time. In practice this means that the results
must contain all the items, in the correct order and without duplicates.

If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
consistent.

The results might become inconsistent if the client either takes too long, or a service instance goes down while
fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.

---

__📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
paginate through the results.

---



<!--</editor-fold>-->
<!-- /typedoc -->

### `PageV2`

<!-- typedoc:dk.sdu.cloud.PageV2:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
Represents a single 'page' of results

| Property | Type | Description |
|----------|------|-------------|
| `itemsPerPage` | `Int` | The expected items per page, this is extracted directly from the request |
| `items` | `Array<Any>` | The items returned in this page |
| `next` | `String` | The token used to fetch additional items from this result set |

    
Every page contains the items from the current result set, along with information which allows the client to fetch
additional information.


<!--</editor-fold>-->
<!-- /typedoc -->

## Error Messages

<!-- typedoc:dk.sdu.cloud.CommonErrorMessage:includeProps=true -->
<!--<editor-fold desc="Generated documentation">-->
A generic error message

| Property | Type | Description |
|----------|------|-------------|
| `why` | `String` | Human readable description of why the error occurred. This value is generally not stable. |
| `errorCode` | `String?` | Machine readable description of why the error occurred. This value is stable and can be relied upon. |


UCloud uses HTTP status code for all error messages. In addition and if possible, UCloud will include a message using a
common format. Note that this is not guaranteed to be included in case of a failure somewhere else in the network stack.
For example, UCloud's load balancer might not be able to contact the backend at all. In such a case UCloud will
_not_ include a more detailed error message.



<!--</editor-fold>-->
<!-- /typedoc -->

## Data Models

In this section, we will cover conventions used for the data models of UCloud. It covers general naming conventions
along with a list of common terminology which should be used throughout UCloud. We also make links to other relevant
data models which are used as a base throughout of UCloud.

We start by covering the naming conventions.This covers the code used in the UCloud `api` sub-projects. That is, the
code which is publicly facing through our RPC interface. These conventions should also be adopted as much as possible
internally in the UCloud code.

### Naming Conventions: Properties

1: Use `camelCase` for all properties

---

2: All abbreviations must use be lower-cased while following the `camelCase` rule

For example:

- Including a URL:
    - Correct: `includeUrl`
    - Incorrect: `includeURL`
- Referencing an ID:
    - Correct: `id`
    - Incorrect: `ID`

---

3: Boolean flags that indicate that data should be included must use the `include` prefix

Examples:

- Correct: `includeBalance`
    - This should cause the `balance` property to be populated in the returned data model
- Incorrect: `withBalance`

---

4: Properties used to filter data must use the `filter` prefix

Examples:

- Correct: `filterArea`
- Correct: `filterProvider`
- Incorrect: `searchByArea` (should use `filter` prefix)
- Incorrect: `area` (prefix missing)

---

### Naming Conventions: Classes

1: Use `PascalCase` for all classes

---

2: Request classes should use the `Request` suffix, response classes should use the `Response` suffix Examples:

- Correct: `JobsRetrieveRequest`
- Correct: `JobsRetrieveResponse`
- Incorrect: `JobsRetrieve` (Missing suffix)

---

3: `CallDescriptionContainer`s should be placed in a file named after the container

- Correct: `Jobs.kt` containing `Jobs`
- Incorrect: `JobDescriptions.kt` containing `Jobs`

---

