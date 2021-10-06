# `FileCollection`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `FileCollection` is an entrypoint to a user's files_

```kotlin
data class FileCollection(
    val id: String,
    val specification: FileCollection.Spec,
    val createdAt: Long,
    val status: FileCollection.Status,
    val updates: List<FileCollection.Update>,
    val owner: ResourceOwner,
    val permissions: ResourcePermissions?,
    val providerGeneratedId: String?,
    val acl: List<ResourceAclEntry>?,
    val billing: ResourceBilling.Free,
)
```
This entrypoint allows the user to access all the files they have access to within a single project. It is important to
note that a file collection is not the same as a directory! Common real-world examples of a file collection is listed
below:

| Name              | Typical path                | Comment                                                     |
|-------------------|-----------------------------|-------------------------------------------------------------|
| Home directory    | `/home/$username/`     | The home folder is typically the main collection for a user |
| Work directory    | `/work/$projectId/`    | The project 'home' folder                                   |
| Scratch directory | `/scratch/$projectId/` | Temporary storage for a project                             |

The provider of storage manages a 'database' of these file collections and who they belong to. The file collections also
play an important role in accounting and resource management. A file collection can have a quota attached to it and
billing information is also stored in this object. Each file collection can be attached to a different product type, and
as a result, can have different billing information attached to it. This is, for example, useful if a storage provider
has both fast and slow tiers of storage, which is typically billed very differently.

All file collections additionally have a title. This title can be used for a user-friendly version of the folder. This
title does not have to be unique, and can with great benefit choose to not reference who it belongs to. For example,
if every user has exactly one home directory, then it would make sense to give this collection `"Home"` as its title.

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
<code>specification</code>: <code><code><a href='#filecollection.spec'>FileCollection.Spec</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#filecollection.status'>FileCollection.Status</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileCollection.Update.md'>FileCollection.Update</a>&gt;</code></code> Contains a list of updates from the provider as well as UCloud
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
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>acl</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceAclEntry.md'>ResourceAclEntry</a>&gt;?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>billing</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceBilling.Free.md'>ResourceBilling.Free</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>

