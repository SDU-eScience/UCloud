<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/providers/drives/ingoing.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/providers/files/ingoing.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Drives (FileCollection)](/docs/developer-guide/orchestration/storage/providers/drives/README.md) / Outgoing API
# Outgoing API

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
<td><a href='#browse'><code>browse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#chargecredits'><code>chargeCredits</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#checkcredits'><code>checkCredits</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#register'><code>register</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#update'><code>update</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags.md'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollectionIncludeFlags.md'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.md'>FileCollection</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `chargeCredits`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits.md'>ResourceChargeCredits</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceChargeCreditsResponse.md'>ResourceChargeCreditsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `checkCredits`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceChargeCredits.md'>ResourceChargeCredits</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceChargeCreditsResponse.md'>ResourceChargeCreditsResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `register`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.Spec.md'>FileCollection.Spec</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `update`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceUpdateAndId.md'>ResourceUpdateAndId</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.Update.md'>FileCollection.Update</a>&gt;&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



