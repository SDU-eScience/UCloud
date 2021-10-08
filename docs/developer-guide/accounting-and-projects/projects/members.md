<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/projects/projects.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/projects/groups.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Projects](/docs/developer-guide/accounting-and-projects/projects/README.md) / Members
# Members

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_UCloud Projects have one or more members._

## Rationale

This API will likely be combined with one or more related APIs in the project feature.

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
<td><a href='#count'><code>count</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#userstatus'><code>userStatus</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupadmins'><code>lookupAdmins</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupadminsbulk'><code>lookupAdminsBulk</code></a></td>
<td><i>No description</i></td>
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
<td><a href='#pair'><code>Pair</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#usergroupsummary'><code>UserGroupSummary</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#userstatusinproject'><code>UserStatusInProject</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupadminsbulkrequest'><code>LookupAdminsBulkRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupadminsrequest'><code>LookupAdminsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#searchrequest'><code>SearchRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#userstatusrequest'><code>UserStatusRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupadminsbulkresponse'><code>LookupAdminsBulkResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#lookupadminsresponse'><code>LookupAdminsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#userstatusresponse'><code>UserStatusResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `count`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `search`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#searchrequest'>SearchRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='/docs/reference/dk.sdu.cloud.project.api.ProjectMember.md'>ProjectMember</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `userStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#userstatusrequest'>UserStatusRequest</a></code>|<code><a href='#userstatusresponse'>UserStatusResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `lookupAdmins`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#lookupadminsrequest'>LookupAdminsRequest</a></code>|<code><a href='#lookupadminsresponse'>LookupAdminsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `lookupAdminsBulk`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#lookupadminsbulkrequest'>LookupAdminsBulkRequest</a></code>|<code><a href='#lookupadminsbulkresponse'>LookupAdminsBulkResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Pair`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Pair<A, B>(
    val first: A,
    val second: B,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>first</code>: <code><code>A</code></code>
</summary>





</details>

<details>
<summary>
<code>second</code>: <code><code>B</code></code>
</summary>





</details>



</details>



---

### `UserGroupSummary`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserGroupSummary(
    val project: String,
    val group: String,
    val username: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>project</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>group</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `UserStatusInProject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserStatusInProject(
    val projectId: String,
    val title: String,
    val whoami: ProjectMember,
    val parent: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>whoami</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.project.api.ProjectMember.md'>ProjectMember</a></code></code>
</summary>





</details>

<details>
<summary>
<code>parent</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `LookupAdminsBulkRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupAdminsBulkRequest(
    val projectId: List<String>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `LookupAdminsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupAdminsRequest(
    val projectId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `SearchRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SearchRequest(
    val query: String,
    val notInGroup: String?,
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
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>notInGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

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

### `UserStatusRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserStatusRequest(
    val username: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `LookupAdminsBulkResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupAdminsBulkResponse(
    val admins: List<Pair<String, List<ProjectMember>>>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>admins</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#pair'>Pair</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>, <a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.project.api.ProjectMember.md'>ProjectMember</a>&gt;&gt;&gt;</code></code>
</summary>





</details>



</details>



---

### `LookupAdminsResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LookupAdminsResponse(
    val admins: List<ProjectMember>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>admins</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.project.api.ProjectMember.md'>ProjectMember</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `UserStatusResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UserStatusResponse(
    val membership: List<UserStatusInProject>,
    val groups: List<UserGroupSummary>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>membership</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#userstatusinproject'>UserStatusInProject</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>groups</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#usergroupsummary'>UserGroupSummary</a>&gt;</code></code>
</summary>





</details>



</details>



---

