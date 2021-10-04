# Public IPs (NetworkIP)

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


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
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL attached to a resource</td>
</tr>
<tr>
<td><a href='#updatefirewall'><code>updateFirewall</code></a></td>
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
<td><a href='#firewallandid'><code>FirewallAndId</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ipprotocol'><code>IPProtocol</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkip'><code>NetworkIP</code></a></td>
<td>A `NetworkIP` for use in `Job`s</td>
</tr>
<tr>
<td><a href='#networkipflags'><code>NetworkIPFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipspecification'><code>NetworkIPSpecification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipspecification.firewall'><code>NetworkIPSpecification.Firewall</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipstate'><code>NetworkIPState</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipstatus'><code>NetworkIPStatus</code></a></td>
<td>The status of an `NetworkIP`</td>
</tr>
<tr>
<td><a href='#networkipsupport'><code>NetworkIPSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipsupport.firewall'><code>NetworkIPSupport.Firewall</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipupdate'><code>NetworkIPUpdate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#portrangeandproto'><code>PortRangeAndProto</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browse`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Browses the catalogue of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#networkipflags'>NetworkIPFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#networkip'>NetworkIP</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#networkipflags'>NetworkIPFlags</a>&gt;</code>|<code><a href='#networkip'>NetworkIP</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>, <a href='#networkipsupport'>NetworkIPSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `search`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Searches the catalogue of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#networkipflags'>NetworkIPFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#networkip'>NetworkIP</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#networkipspecification'>NetworkIPSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Deletes one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateFirewall`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#firewallandid'>FirewallAndId</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FirewallAndId`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class FirewallAndId(
    val id: String,
    val firewall: NetworkIPSpecification.Firewall,
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
<code>firewall</code>: <code><code><a href='#networkipspecification.firewall'>NetworkIPSpecification.Firewall</a></code></code>
</summary>





</details>



</details>



---

### `IPProtocol`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
enum class IPProtocol {
    TCP,
    UDP,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>TCP</code>
</summary>





</details>

<details>
<summary>
<code>UDP</code>
</summary>





</details>



</details>



---

### `NetworkIP`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


_A `NetworkIP` for use in `Job`s_

```kotlin
data class NetworkIP(
    val id: String,
    val specification: NetworkIPSpecification,
    val owner: ResourceOwner,
    val createdAt: Long,
    val status: NetworkIPStatus,
    val updates: List<NetworkIPUpdate>?,
    val resolvedProduct: Product.NetworkIP?,
    val permissions: ResourcePermissions?,
    val acl: List<ResourceAclEntry>?,
    val billing: ResourceBilling.Free,
    val providerGeneratedId: String?,
)
```

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
<code>specification</code>: <code><code><a href='#networkipspecification'>NetworkIPSpecification</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> Information about the owner of this resource
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Information about when this resource was created
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#networkipstatus'>NetworkIPStatus</a></code></code> The current status of this resource
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#networkipupdate'>NetworkIPUpdate</a>&gt;?</code></code> A list of updates for this `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>?</code></code>
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

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)




</details>

<details>
<summary>
<code>billing</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceBilling.Free.md'>ResourceBilling.Free</a></code></code>
</summary>

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)




</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)




</details>



</details>



---

### `NetworkIPFlags`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class NetworkIPFlags(
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

### `NetworkIPSpecification`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class NetworkIPSpecification(
    val product: ProductReference,
    val firewall: NetworkIPSpecification.Firewall?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> The product used for the `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>firewall</code>: <code><code><a href='#networkipspecification.firewall'>NetworkIPSpecification.Firewall</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPSpecification.Firewall`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Firewall(
    val openPorts: List<PortRangeAndProto>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>openPorts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#portrangeandproto'>PortRangeAndProto</a>&gt;?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPState`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
enum class NetworkIPState {
    PREPARING,
    READY,
    UNAVAILABLE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PREPARING</code> A state indicating that the `NetworkIP` is currently being prepared and is expected to reach `READY` soon.
</summary>





</details>

<details>
<summary>
<code>READY</code> A state indicating that the `NetworkIP` is ready for use or already in use.
</summary>





</details>

<details>
<summary>
<code>UNAVAILABLE</code> A state indicating that the `NetworkIP` is currently unavailable.
</summary>



This state can be used to indicate downtime or service interruptions by the provider.


</details>



</details>



---

### `NetworkIPStatus`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


_The status of an `NetworkIP`_

```kotlin
data class NetworkIPStatus(
    val state: NetworkIPState,
    val boundTo: List<String>?,
    val ipAddress: String?,
    val resolvedSupport: ResolvedSupport<Product.NetworkIP, NetworkIPSupport>?,
    val resolvedProduct: Product.NetworkIP?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#networkipstate'>NetworkIPState</a></code></code>
</summary>





</details>

<details>
<summary>
<code>boundTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The ID of the `Job` that this `NetworkIP` is currently bound to
</summary>





</details>

<details>
<summary>
<code>ipAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The externally accessible IP address allocated to this `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>, <a href='#networkipsupport'>NetworkIPSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `NetworkIPSupport`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class NetworkIPSupport(
    val product: ProductReference,
    val firewall: NetworkIPSupport.Firewall?,
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
<code>firewall</code>: <code><code><a href='#networkipsupport.firewall'>NetworkIPSupport.Firewall</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPSupport.Firewall`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class Firewall(
    val enabled: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPUpdate`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
data class NetworkIPUpdate(
    val timestamp: Long?,
    val state: NetworkIPState?,
    val status: String?,
    val changeIpAddress: Boolean?,
    val newIpAddress: String?,
    val binding: JobBinding?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this update was registered by UCloud
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#networkipstate'>NetworkIPState</a>?</code></code> The new state that the `NetworkIP` transitioned to (if any)
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A new status message for the `NetworkIP` (if any)
</summary>





</details>

<details>
<summary>
<code>changeIpAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newIpAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>binding</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobBinding.md'>JobBinding</a>?</code></code>
</summary>





</details>



</details>



---

### `PortRangeAndProto`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class PortRangeAndProto(
    val start: Int,
    val end: Int,
    val protocol: IPProtocol,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>start</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>end</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>protocol</code>: <code><code><a href='#ipprotocol'>IPProtocol</a></code></code>
</summary>





</details>



</details>



---

