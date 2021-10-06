# `FileMetadataTemplateNamespace`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Resource` is the core data model used to synchronize tasks between UCloud and a_

```kotlin
data class FileMetadataTemplateNamespace(
    val id: String,
    val specification: FileMetadataTemplateNamespace.Spec,
    val createdAt: Long,
    val status: FileMetadataTemplateNamespace.Status,
    val updates: List<FileMetadataTemplateNamespace.Update>,
    val owner: ResourceOwner,
    val permissions: ResourcePermissions?,
    val acl: List<ResourceAclEntry>?,
    val billing: ResourceBilling.Free,
    val providerGeneratedId: String?,
)
```
[provider](/backend/provider-service/README.md).

`Resource`s provide instructions to providers on how they should complete a given task. Examples of a `Resource`
include: [Compute jobs](/backend/app-orchestrator-service/README.md), HTTP ingress points and license servers. For
example, a (compute) `Job` provides instructions to the provider on how to start a software computation. It also gives
the provider APIs for communicating the status of the `Job`.

All `Resource` share a common interface and data model. The data model contains a specification of the `Resource`, along
with metadata, such as: ownership, billing and status.

`Resource`s are created in UCloud when a user requests it. This request is verified by UCloud and forwarded to the
provider. It is then up to the provider to implement the functionality of the `Resource`.

![](/backend/provider-service/wiki/resource_create.svg)

__Figure:__ UCloud orchestrates with the provider to create a `Resource`

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique identifier referencing the `Resource`
</summary>



The ID is unique across a provider for a single resource type.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#filemetadatatemplatenamespace.spec'>FileMetadataTemplateNamespace.Spec</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#filemetadatatemplatenamespace.status'>FileMetadataTemplateNamespace.Status</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplateNamespace.Update.md'>FileMetadataTemplateNamespace.Update</a>&gt;</code></code> Contains a list of updates from the provider as well as UCloud
</summary>



Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> Contains information about the original creator of the `Resource` along with project association
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>acl</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>billing</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceBilling.Free.md'>ResourceBilling.Free</a></code></code>
</summary>





</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>

