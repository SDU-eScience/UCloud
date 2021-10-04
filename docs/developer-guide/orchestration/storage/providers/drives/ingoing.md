# Ingoing API

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


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
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#rename'><code>rename</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#verify'><code>verify</code></a></td>
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
<td><a href='#filecollectionsproviderrenamerequestitem'><code>FileCollectionsProviderRenameRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrieveProducts`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FSSupport.md'>FSSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rename`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filecollectionsproviderrenamerequestitem'>FileCollectionsProviderRenameRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAclWithResource.md'>UpdatedAclWithResource</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a>&gt;&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `verify`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FileCollectionsProviderRenameRequestItem`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class FileCollectionsProviderRenameRequestItem(
    val id: String,
    val newTitle: String,
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

<details>
<summary>
<code>newTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

