<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/grants/grants.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/resources.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / Gifts
# Gifts

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Gifts provide the system a way to grant new and existing users (personal projects) credits from a project_

## Rationale

The gifting system is primarily intended to provide credits to new users when they join the system. A 
[`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md)  follows the same rules as [`Wallets`](/docs/reference/dk.sdu.cloud.accounting.api.Wallets.md)  do. This means that in order to 
give a gift to someone you must transfer the resources from a project. This means that the credits will be subtracted
directly from the source project.

A [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md)  can only be claimed once by every user and it will be applied directly to the user's personal project.
Clients used by the end-user should use `Gifts.availableGifts` to figure out which [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md)s are unclaimed by
this user. It may then claim the individual [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md)s with `Gifts.claimGift`.

Administrators of a project can manage [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md)s through the `Gifts.createGift`, `Gifts.deleteGift` and
`Gifts.listGifts` endpoints.           

---
    
__⚠️ WARNING:__ The API listed on this page will likely change to conform with our
[API conventions](/docs/developer-guide/core/api-conventions.md). Be careful when building integrations. The following
changes are expected:

- RPC names will change to conform with the conventions
- RPC request and response types will change to conform with the conventions
- RPCs which return a page will be collapsed into a single `browse` endpoint
- Some property names will change to be consistent with [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)s

---

## Table of Contents
<details>
<summary>
<a href='#remote-procedure-calls'>1. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#browse'><code>browse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#availablegifts'><code>availableGifts</code></a></td>
<td>Finds a list of a user's unclaimed `Gift`s</td>
</tr>
<tr>
<td><a href='#claimgift'><code>claimGift</code></a></td>
<td>Claims a `Gift` to the calling user's personal project</td>
</tr>
<tr>
<td><a href='#creategift'><code>createGift</code></a></td>
<td>Creates a new Gift in the system</td>
</tr>
<tr>
<td><a href='#deletegift'><code>deleteGift</code></a></td>
<td>Deletes a Gift by its DeleteGiftRequest.giftId</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>2. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#giftwithcriteria'><code>GiftWithCriteria</code></a></td>
<td>A `Gift` along with the `criteria` for which that can `Gifts.claimGift` this</td>
</tr>
<tr>
<td><a href='#claimgiftrequest'><code>ClaimGiftRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deletegiftrequest'><code>DeleteGiftRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#gifts.browse.request'><code>Gifts.Browse.Request</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#availablegiftsresponse'><code>AvailableGiftsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browse`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#gifts.browse.request'>Gifts.Browse.Request</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#giftwithcriteria'>GiftWithCriteria</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `availableGifts`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Finds a list of a user's unclaimed `Gift`s_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#availablegiftsresponse'>AvailableGiftsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `claimGift`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Claims a `Gift` to the calling user's personal project_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#claimgiftrequest'>ClaimGiftRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

User errors:
 - Users who are not eligible for claiming this `Gift` will receive an appropriate error code.
 - If the gifting project has run out of resources then this endpoint will fail. The gift will not be 
   marked as claimed.


### `createGift`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new Gift in the system_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#giftwithcriteria'>GiftWithCriteria</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.FindByLongId.md'>FindByLongId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only project administrators can create new [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md)s in the system.


### `deleteGift`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes a Gift by its DeleteGiftRequest.giftId_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#deletegiftrequest'>DeleteGiftRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only project administrators of `Gift.resourcesOwnedBy` can delete the [`Gift`](/docs/reference/dk.sdu.cloud.grant.api.Gift.md).



## Data Models

### `GiftWithCriteria`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Gift` along with the `criteria` for which that can `Gifts.claimGift` this_

```kotlin
data class GiftWithCriteria(
    val id: Long,
    val resourcesOwnedBy: String,
    val title: String,
    val description: String,
    val resources: List<GrantApplication.AllocationRequest>,
    val renewEvery: Int,
    val criteria: List<UserCriteria>,
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

<details>
<summary>
<code>resourcesOwnedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A reference to the project which owns these resources
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The title of a gift
</summary>



Suitable for presentation in UIs


</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The title of a gift
</summary>



Suitable for presentation in UIs


</details>

<details>
<summary>
<code>resources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.grant.api.GrantApplication.AllocationRequest.md'>GrantApplication.AllocationRequest</a>&gt;</code></code> A list of resources which will be granted to users `Gifts.claimGift` this `Gift`.
</summary>





</details>

<details>
<summary>
<code>renewEvery</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> Renewal policy for the gift
</summary>





</details>

<details>
<summary>
<code>criteria</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.grant.api.UserCriteria.md'>UserCriteria</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ClaimGiftRequest`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ClaimGiftRequest(
    val giftId: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>giftId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `DeleteGiftRequest`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DeleteGiftRequest(
    val giftId: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>giftId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>



</details>



---

### `Gifts.Browse.Request`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class Request(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
)
```
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

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
</summary>





</details>

<details>
<summary>
<code>next</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A token requesting the next page of items
</summary>





</details>

<details>
<summary>
<code>consistency</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.PaginationRequestV2Consistency.md'>PaginationRequestV2Consistency</a>?</code></code> Controls the consistency guarantees provided by the backend
</summary>





</details>

<details>
<summary>
<code>itemsToSkip</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Items to skip ahead
</summary>





</details>



</details>



---

### `AvailableGiftsResponse`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AvailableGiftsResponse(
    val gifts: List<FindByLongId>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>gifts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByLongId.md'>FindByLongId</a>&gt;</code></code>
</summary>





</details>



</details>



---

