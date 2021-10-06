<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/providers/drives/outgoing.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/providers/files/outgoing.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Files](/docs/developer-guide/orchestration/storage/providers/files/README.md) / Ingoing API
# Ingoing API

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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
<td>Retrieve product support for this providers</td>
</tr>
<tr>
<td><a href='#browse'><code>browse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#copy'><code>copy</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createdownload'><code>createDownload</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createfolder'><code>createFolder</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createupload'><code>createUpload</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#emptytrash'><code>emptyTrash</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#move'><code>move</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#trash'><code>trash</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Callback received by the Provider when permissions are updated</td>
</tr>
<tr>
<td><a href='#verify'><code>verify</code></a></td>
<td>Invoked by UCloud/Core to trigger verification of a single batch</td>
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
<td><a href='#partialufile'><code>PartialUFile</code></a></td>
<td>A partial UFile returned by providers and made complete by UCloud/Core</td>
</tr>
<tr>
<td><a href='#filesproviderbrowserequest'><code>FilesProviderBrowseRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovidercopyrequestitem'><code>FilesProviderCopyRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovidercreatedownloadrequestitem'><code>FilesProviderCreateDownloadRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovidercreatefolderrequestitem'><code>FilesProviderCreateFolderRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovidercreateuploadrequestitem'><code>FilesProviderCreateUploadRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovideremptytrashrequestitem'><code>FilesProviderEmptyTrashRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovidermoverequestitem'><code>FilesProviderMoveRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesproviderretrieverequest'><code>FilesProviderRetrieveRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filesprovidertrashrequestitem'><code>FilesProviderTrashRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for this providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FSSupport.md'>FSSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint responds with the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  s supported by
this provider along with details for how [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  is
supported. The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  s must be registered with
UCloud/Core already.


### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filesproviderbrowserequest'>FilesProviderBrowseRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#partialufile'>PartialUFile</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `copy`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovidercopyrequestitem'>FilesProviderCopyRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.LongRunningTask.md'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md'>UFile</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `createDownload`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovidercreatedownloadrequestitem'>FilesProviderCreateDownloadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FilesCreateDownloadResponseItem.md'>FilesCreateDownloadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `createFolder`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovidercreatefolderrequestitem'>FilesProviderCreateFolderRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.LongRunningTask.md'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `createUpload`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovidercreateuploadrequestitem'>FilesProviderCreateUploadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FilesCreateUploadResponseItem.md'>FilesCreateUploadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md'>UFile</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `emptyTrash`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovideremptytrashrequestitem'>FilesProviderEmptyTrashRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.LongRunningTask.md'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `move`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovidermoverequestitem'>FilesProviderMoveRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.LongRunningTask.md'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filesproviderretrieverequest'>FilesProviderRetrieveRequest</a></code>|<code><a href='#partialufile'>PartialUFile</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `trash`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesprovidertrashrequestitem'>FilesProviderTrashRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.LongRunningTask.md'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Callback received by the Provider when permissions are updated_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAclWithResource.md'>UpdatedAclWithResource</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md'>UFile</a>&gt;&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
internal state, then they may simply ignore this request by responding with `200 OK`. The
Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
not acknowledge the request.


### `verify`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Invoked by UCloud/Core to trigger verification of a single batch_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md'>UFile</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
Provider should immediately determine if these are still valid and recognized by the Provider.
If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
an update for each affected resource.



## Data Models

### `PartialUFile`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A partial UFile returned by providers and made complete by UCloud/Core_

```kotlin
data class PartialUFile(
    val id: String,
    val status: UFileStatus,
    val createdAt: Long,
    val owner: ResourceOwner?,
    val permissions: ResourcePermissions?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The id of the file. Corresponds to UFile.id
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileStatus.md'>UFileStatus</a></code></code> The status of the file. Corresponds to UFile.status
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The creation timestamp. Corresponds to UFile.createdAt
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a>?</code></code> The owner of the file. Corresponds to UFile.owner. This will default to the collection's owner.
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> The permissions of the file. Corresponds to UFile.permissions.This will default to the collection's permissions.
</summary>





</details>



</details>



---

### `FilesProviderBrowseRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderBrowseRequest(
    val resolvedCollection: FileCollection,
    val browse: ResourceBrowseRequest<UFileIncludeFlags>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>browse</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileIncludeFlags.md'>UFileIncludeFlags</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `FilesProviderCopyRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderCopyRequestItem(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    val oldId: String,
    val newId: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedOldCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedNewCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>oldId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy.md'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesProviderCreateDownloadRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderCreateDownloadRequestItem(
    val resolvedCollection: FileCollection,
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FilesProviderCreateFolderRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderCreateFolderRequestItem(
    val resolvedCollection: FileCollection,
    val id: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy.md'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesProviderCreateUploadRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderCreateUploadRequestItem(
    val resolvedCollection: FileCollection,
    val id: String,
    val supportedProtocols: List<UploadProtocol>,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>supportedProtocols</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UploadProtocol.md'>UploadProtocol</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy.md'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesProviderEmptyTrashRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderEmptyTrashRequestItem(
    val resolvedCollection: FileCollection,
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FilesProviderMoveRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderMoveRequestItem(
    val resolvedOldCollection: FileCollection,
    val resolvedNewCollection: FileCollection,
    val oldId: String,
    val newId: String,
    val conflictPolicy: WriteConflictPolicy,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedOldCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedNewCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>oldId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>conflictPolicy</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy.md'>WriteConflictPolicy</a></code></code>
</summary>





</details>



</details>



---

### `FilesProviderRetrieveRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderRetrieveRequest(
    val resolvedCollection: FileCollection,
    val retrieve: ResourceRetrieveRequest<UFileIncludeFlags>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>retrieve</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFileIncludeFlags.md'>UFileIncludeFlags</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `FilesProviderTrashRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FilesProviderTrashRequestItem(
    val resolvedCollection: FileCollection,
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedCollection</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

