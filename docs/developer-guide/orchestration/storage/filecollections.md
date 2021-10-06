[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / Drives (FileCollection)
# Drives (FileCollection)

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
<td>Browses the catalogue of available resources</td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieve a single resource</td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for all accessible providers</td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td>Searches the catalogue of available resources</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates one or more resources</td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td>Deletes one or more resources</td>
</tr>
<tr>
<td><a href='#rename'><code>rename</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL attached to a resource</td>
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
<td><a href='#fscollectionsupport'><code>FSCollectionSupport</code></a></td>
<td>Declares which `FileCollection` operations are supported for a product</td>
</tr>
<tr>
<td><a href='#fsfilesupport'><code>FSFileSupport</code></a></td>
<td>Declares which file-level operations a product supports</td>
</tr>
<tr>
<td><a href='#fsproductstatssupport'><code>FSProductStatsSupport</code></a></td>
<td>Declares which stats a given product supports</td>
</tr>
<tr>
<td><a href='#fssupport'><code>FSSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filecollection'><code>FileCollection</code></a></td>
<td>A `FileCollection` is an entrypoint to a user's files</td>
</tr>
<tr>
<td><a href='#filecollection.spec'><code>FileCollection.Spec</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filecollection.status'><code>FileCollection.Status</code></a></td>
<td>Describes the current state of the `Resource`</td>
</tr>
<tr>
<td><a href='#filecollectionincludeflags'><code>FileCollectionIncludeFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filecollectionsrenamerequestitem'><code>FileCollectionsRenameRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalogue of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#filecollectionincludeflags'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#filecollection'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#filecollectionincludeflags'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='#filecollection'>FileCollection</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='#fssupport'>FSSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `search`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalogue of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#filecollectionincludeflags'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#filecollection'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filecollection.spec'>FileCollection.Spec</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `rename`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filecollectionsrenamerequestitem'>FileCollectionsRenameRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FSCollectionSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which `FileCollection` operations are supported for a product_

```kotlin
data class FSCollectionSupport(
    val aclModifiable: Boolean?,
    val usersCanCreate: Boolean?,
    val usersCanDelete: Boolean?,
    val usersCanRename: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>aclModifiable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usersCanCreate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usersCanDelete</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usersCanRename</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `FSFileSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which file-level operations a product supports_

```kotlin
data class FSFileSupport(
    val aclModifiable: Boolean?,
    val trashSupported: Boolean?,
    val isReadOnly: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>aclModifiable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>trashSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isReadOnly</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `FSProductStatsSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which stats a given product supports_

```kotlin
data class FSProductStatsSupport(
    val sizeInBytes: Boolean?,
    val sizeIncludingChildrenInBytes: Boolean?,
    val modifiedAt: Boolean?,
    val createdAt: Boolean?,
    val accessedAt: Boolean?,
    val unixPermissions: Boolean?,
    val unixOwner: Boolean?,
    val unixGroup: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sizeInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>sizeIncludingChildrenInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>accessedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixPermissions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixOwner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `FSSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FSSupport(
    val product: ProductReference,
    val stats: FSProductStatsSupport?,
    val collection: FSCollectionSupport?,
    val files: FSFileSupport?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>stats</code>: <code><code><a href='#fsproductstatssupport'>FSProductStatsSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>collection</code>: <code><code><a href='#fscollectionsupport'>FSCollectionSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>files</code>: <code><code><a href='#fsfilesupport'>FSFileSupport</a>?</code></code>
</summary>





</details>



</details>



---

### `FileCollection`

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



---

### `FileCollection.Spec`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Spec(
    val title: String,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which backs this `Resource`
</summary>





</details>



</details>



---

### `FileCollection.Status`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class Status(
    val resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>?,
    val resolvedProduct: Product.Storage?,
)
```
The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='#fssupport'>FSSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `FileCollectionIncludeFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileCollectionIncludeFlags(
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val includeProduct: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterIds: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
</summary>





</details>

<details>
<summary>
<code>filterCreatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProviderIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the provider ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>filterIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the resource ID. The value is comma-separated.
</summary>





</details>



</details>



---

### `FileCollectionsRenameRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileCollectionsRenameRequestItem(
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

