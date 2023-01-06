<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/grants/grant-settings-logo.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/grants/gifts.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Grants](/docs/developer-guide/accounting-and-projects/grants/README.md) / Grant Settings (Description)
# Grant Settings (Description)

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_A project can upload a description that is shown to the users when they are looking for a grant giver._

## Rationale

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
<td><a href='#retrievedescription'><code>retrieveDescription</code></a></td>
<td>Fetches a description of a project</td>
</tr>
<tr>
<td><a href='#uploaddescription'><code>uploadDescription</code></a></td>
<td>Uploads descriptions of projects</td>
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
<td><a href='#retrievedescriptionrequest'><code>RetrieveDescriptionRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploaddescriptionrequest'><code>UploadDescriptionRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrievedescriptionresponse'><code>RetrieveDescriptionResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrieveDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Fetches a description of a project_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#retrievedescriptionrequest'>RetrieveDescriptionRequest</a></code>|<code><a href='#retrievedescriptionresponse'>RetrieveDescriptionResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Uploads descriptions of projects_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#uploaddescriptionrequest'>UploadDescriptionRequest</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `RetrieveDescriptionRequest`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RetrieveDescriptionRequest(
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

### `UploadDescriptionRequest`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UploadDescriptionRequest(
    val projectId: String,
    val description: String,
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
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `RetrieveDescriptionResponse`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class RetrieveDescriptionResponse(
    val description: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

