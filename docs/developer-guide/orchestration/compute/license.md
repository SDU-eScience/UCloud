# Software Licenses

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



## Remote Procedure Calls

### `create`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#licensespecification'>LicenseSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `search`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#licenseincludeflags'>LicenseIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#license'>License</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `browse`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#licenseincludeflags'>LicenseIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#license'>License</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#licenseincludeflags'>LicenseIncludeFlags</a>&gt;</code>|<code><a href='#license'>License</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.License.md'>Product.License</a>, <a href='#licensesupport'>LicenseSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `License`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)


_A `License` for use in `Job`s_

```kotlin
data class License(
    val id: String,
    val specification: LicenseSpecification,
    val owner: ResourceOwner,
    val createdAt: Long,
    val status: LicenseStatus,
    val updates: List<LicenseUpdate>?,
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
<code>specification</code>: <code><code><a href='#licensespecification'>LicenseSpecification</a></code></code>
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
<code>status</code>: <code><code><a href='#licensestatus'>LicenseStatus</a></code></code> The current status of this resource
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#licenseupdate'>LicenseUpdate</a>&gt;?</code></code> A list of updates for this `License`
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

### `LicenseIncludeFlags`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class LicenseIncludeFlags(
    val includeOthers: Boolean,
    val includeUpdates: Boolean,
    val includeSupport: Boolean,
    val includeProduct: Boolean,
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
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Includes `specification.resolvedProduct`
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

### `LicenseSpecification`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class LicenseSpecification(
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> The product used for the `License`
</summary>





</details>



</details>



---

### `LicenseState`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
enum class LicenseState {
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
<code>PREPARING</code> A state indicating that the `License` is currently being prepared and is expected to reach `READY` soon.
</summary>





</details>

<details>
<summary>
<code>READY</code> A state indicating that the `License` is ready for use or already in use.
</summary>





</details>

<details>
<summary>
<code>UNAVAILABLE</code> A state indicating that the `License` is currently unavailable.
</summary>



This state can be used to indicate downtime or service interruptions by the provider.


</details>



</details>



---

### `LicenseStatus`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)


_The status of an `License`_

```kotlin
data class LicenseStatus(
    val state: LicenseState,
    val resolvedSupport: ResolvedSupport<Product.License, LicenseSupport>?,
    val resolvedProduct: Product.License?,
    val boundTo: List<String>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#licensestate'>LicenseState</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.License.md'>Product.License</a>, <a href='#licensesupport'>LicenseSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.License.md'>Product.License</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>

<details>
<summary>
<code>boundTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The IDs of the `Job`s that this `Resource` is currently bound to
</summary>





</details>



</details>



---

### `LicenseSupport`

![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class LicenseSupport(
    val product: ProductReference,
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



</details>



---

### `LicenseUpdate`

![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)



```kotlin
data class LicenseUpdate(
    val timestamp: Long?,
    val state: LicenseState?,
    val status: String?,
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
<code>state</code>: <code><code><a href='#licensestate'>LicenseState</a>?</code></code> The new state that the `License` transitioned to (if any)
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A new status message for the `License` (if any)
</summary>





</details>

<details>
<summary>
<code>binding</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobBinding.md'>JobBinding</a>?</code></code>
</summary>





</details>



</details>



---

