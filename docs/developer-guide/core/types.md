<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/providers/README.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/api-conventions.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / Core Types
# Core Types

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


## Table of Contents
<details>
<summary>
<a href='#data-models'>1. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#commonerrormessage'><code>CommonErrorMessage</code></a></td>
<td>A generic error message</td>
</tr>
<tr>
<td><a href='#findbylongid'><code>FindByLongId</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findbystringid'><code>FindByStringId</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#page'><code>Page</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#pagev2'><code>PageV2</code></a></td>
<td>Represents a single 'page' of results</td>
</tr>
<tr>
<td><a href='#role'><code>Role</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#securityprincipal'><code>SecurityPrincipal</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#paginationrequest'><code>PaginationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#paginationrequestv2consistency'><code>PaginationRequestV2Consistency</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#bulkrequest'><code>BulkRequest</code></a></td>
<td>A base type for requesting a bulk operation.</td>
</tr>
<tr>
<td><a href='#bulkresponse'><code>BulkResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Data Models

### `CommonErrorMessage`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A generic error message_

```kotlin
data class CommonErrorMessage(
    val why: String,
    val errorCode: String?,
)
```
UCloud uses HTTP status code for all error messages. In addition and if possible, UCloud will include a message
using a common format. Note that this is not guaranteed to be included in case of a failure somewhere else in
the network stack. For example, UCloud's load balancer might not be able to contact the backend at all. In
such a case UCloud will _not_ include a more detailed error message.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>why</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Human readable description of why the error occurred. This value is generally not stable.
</summary>





</details>

<details>
<summary>
<code>errorCode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Machine readable description of why the error occurred. This value is stable and can be relied upon.
</summary>





</details>



</details>



---

### `FindByLongId`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindByLongId(
    val id: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `FindByStringId`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindByStringId(
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `Page`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Page<T>(
    val itemsInTotal: Int,
    val itemsPerPage: Int,
    val pageNumber: Int,
    val items: List<T>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsInTotal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>pageNumber</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>items</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;T&gt;</code></code>
</summary>





</details>



</details>



---

### `PageV2`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Represents a single 'page' of results_

```kotlin
data class PageV2<T>(
    val itemsPerPage: Int,
    val items: List<T>,
    val next: String?,
)
```
Every page contains the items from the current result set, along with information which allows the client to fetch
additional information.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The expected items per page, this is extracted directly from the request
</summary>





</details>

<details>
<summary>
<code>items</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;T&gt;</code></code> The items returned in this page
</summary>



NOTE: The amount of items might differ from `itemsPerPage`, even if there are more results. The only reliable way to
check if the end of results has been reached is by checking i `next == null`.


</details>

<details>
<summary>
<code>next</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The token used to fetch additional items from this result set
</summary>





</details>



</details>



---

### `Role`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Role {
    GUEST,
    USER,
    ADMIN,
    SERVICE,
    THIRD_PARTY_APP,
    PROVIDER,
    UNKNOWN,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>GUEST</code>
</summary>





</details>

<details>
<summary>
<code>USER</code>
</summary>





</details>

<details>
<summary>
<code>ADMIN</code>
</summary>





</details>

<details>
<summary>
<code>SERVICE</code>
</summary>





</details>

<details>
<summary>
<code>THIRD_PARTY_APP</code>
</summary>





</details>

<details>
<summary>
<code>PROVIDER</code>
</summary>





</details>

<details>
<summary>
<code>UNKNOWN</code>
</summary>





</details>



</details>



---

### `SecurityPrincipal`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SecurityPrincipal(
    val username: String,
    val role: Role,
    val firstName: String,
    val lastName: String,
    val uid: Long,
    val email: String?,
    val twoFactorAuthentication: Boolean?,
    val principalType: String?,
    val serviceAgreementAccepted: Boolean?,
    val organization: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>role</code>: <code><code><a href='#role'>Role</a></code></code>
</summary>





</details>

<details>
<summary>
<code>firstName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>lastName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>uid</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>email</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>twoFactorAuthentication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>principalType</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>serviceAgreementAccepted</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>organization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `PaginationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PaginationRequest(
    val itemsPerPage: Int?,
    val page: Int?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `PaginationRequestV2Consistency`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class PaginationRequestV2Consistency {
    PREFER,
    REQUIRE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PREFER</code> Consistency is preferred but not required. An inconsistent snapshot might be returned.
</summary>





</details>

<details>
<summary>
<code>REQUIRE</code> Consistency is required. A request will fail if consistency is no longer guaranteed.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>



---

### `BulkRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A base type for requesting a bulk operation._

```kotlin
data class BulkRequest<T>(
    val items: List<T>,
)
```
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

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>items</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;T&gt;</code></code>
</summary>





</details>



</details>



---

### `BulkResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class BulkResponse<T>(
    val responses: List<T>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>responses</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;T&gt;</code></code>
</summary>





</details>



</details>



---

