<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/providers.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/accounting/allocations.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / Products
# Products

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Products define the services exposed by a Provider._

## Rationale

[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s expose services into UCloud. But, different 
[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s expose different services. UCloud uses [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md)s to define the 
services of a [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md). As an example, a 
[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  might have the following services:

- __Storage:__ Two tiers of storage. Fast storage, for short-lived data. Slower storage, for long-term data storage.
- __Compute:__ Three tiers of compute. Slim nodes for ordinary computations. Fat nodes for memory-hungry applications. 
  GPU powered nodes for artificial intelligence.

For many [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s, the story doesn't stop here. You can often allocate your 
[`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s on a machine "slice". This can increase overall utilization, as users 
aren't forced to request full nodes. A [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  might advertise the following
slices:

| Name | vCPU | RAM (GB) | GPU | Price |
|------|------|----------|-----|-------|
| `example-slim-1` | 1 | 4 | 0 | 0,100 DKK/hr |
| `example-slim-2` | 2 | 8 | 0 | 0,200 DKK/hr |
| `example-slim-4` | 4 | 16 | 0 | 0,400 DKK/hr |
| `example-slim-8` | 8 | 32 | 0 | 0,800 DKK/hr |

__Table:__ A single node-type split up into individual slices.

## Concepts

UCloud represent these concepts in the following abstractions:

- [`ProductType`](/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md): A classifier for a [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md), defines the behavior of a [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md).
- [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md): A group of similar [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s. In most cases, [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s in a category
  run on identical hardware. 
- [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md): Defines a concrete service exposed by a [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider..md) 

Below, we show an example of how a [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  can organize their services.

![](/backend/accounting-service/wiki/products.png)

__Figure:__ All [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md)s in UCloud are of a specific type, such as: `STORAGE` and `COMPUTE`.
[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s have zero or more categories of every type, e.g. `example-slim`. 
In a given category, the [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  has one or more slices.

## Payment Model

UCloud uses a flexible payment model, which allows [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s to use a model which
is familiar to them. The payment model is defined in the [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory..md)  Here the provider will define the
[`AccountingUnit`](/docs/reference/dk.sdu.cloud.accounting.api.AccountingUnit.md)  and [`AccountingFrequency`](/docs/reference/dk.sdu.cloud.accounting.api.AccountingFrequency..md)  The unit describes the unit in which the provider reports
usage. This unit is opaque to UCloud/Core and UCloud/Core will not attempt to convert or in any way interpret the
meaning of this unit. The frequency describes how often a charge occurs. These are either periodic or non-periodic.
UCloud's accounting system has slightly different rules depending on if a product is periodic or non-periodic. Providers
are not required to report once for have period. The frequency simply tells UCloud/Core how to interpret the unit. For
example a combination of `unit = Core` and `frequency = PERIODIC_MINUTE` should be interpreted as core-minutes. UCloud's
frontend may choose to convert this into core-hours for better readability, but the internal numbers are stored in
minutes.

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
<td>Browse a set of products</td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieve a single product</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  in UCloud</td>
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
<td><a href='#accountingfrequency'><code>AccountingFrequency</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#accountingunit'><code>AccountingUnit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#productcategory'><code>ProductCategory</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#producttype'><code>ProductType</code></a></td>
<td>A classifier for a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)</td>
</tr>
<tr>
<td><a href='#productv2'><code>ProductV2</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#productv2.compute'><code>ProductV2.Compute</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#productv2.ingress'><code>ProductV2.Ingress</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#productv2.license'><code>ProductV2.License</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#productv2.networkip'><code>ProductV2.NetworkIP</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#productv2.storage'><code>ProductV2.Storage</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#productsv2browserequest'><code>ProductsV2BrowseRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#productsv2retrieverequest'><code>ProductsV2RetrieveRequest</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browse a set of products_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#productsv2browserequest'>ProductsV2BrowseRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#productv2'>ProductV2</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint uses the normal pagination and filter mechanisms to return a list of [`ProductV2`](/docs/reference/dk.sdu.cloud.accounting.api.ProductV2.md).

__Examples:__

| Example |
|---------|
| [Browse in the full product catalog](/docs/reference/products.v2_browse.md) |
| [Browse for a specific type of product (e.g. compute)](/docs/reference/products.v2_browse-by-type.md) |


### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single product_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#productsv2retrieverequest'>ProductsV2RetrieveRequest</a></code>|<code><a href='#productv2'>ProductV2</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|


__Examples:__

| Example |
|---------|
| [Retrieving a single product by ID](/docs/reference/products.v2_retrieve.md) |


### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: SERVICE, ADMIN, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=SERVICE,+ADMIN,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  in UCloud_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#productv2'>ProductV2</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only providers and UCloud administrators can create a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md). When this endpoint is
invoked by a provider, then the provider field of the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  must match the invoking user.

The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  will become ready and visible in UCloud immediately after invoking this call.
If no [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  has been created in this category before, then this category will be created.

---

__📝 NOTE:__ Most properties of a [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md)  are immutable and must not be changed.
As a result, you cannot create a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  later with different category properties.

---

If the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  already exists, then the existing product is overwritten.



## Data Models

### `AccountingFrequency`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class AccountingFrequency {
    ONCE,
    PERIODIC_MINUTE,
    PERIODIC_HOUR,
    PERIODIC_DAY,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ONCE</code>
</summary>





</details>

<details>
<summary>
<code>PERIODIC_MINUTE</code>
</summary>





</details>

<details>
<summary>
<code>PERIODIC_HOUR</code>
</summary>





</details>

<details>
<summary>
<code>PERIODIC_DAY</code>
</summary>





</details>



</details>



---

### `AccountingUnit`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AccountingUnit(
    val name: String,
    val namePlural: String,
    val floatingPoint: Boolean,
    val displayFrequencySuffix: Boolean,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>namePlural</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>floatingPoint</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>displayFrequencySuffix</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `ProductCategory`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProductCategory(
    val name: String,
    val provider: String,
    val productType: ProductType,
    val accountingUnit: AccountingUnit,
    val accountingFrequency: AccountingFrequency,
    val freeToUse: Boolean?,
    val allowSubAllocations: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>provider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>accountingUnit</code>: <code><code><a href='#accountingunit'>AccountingUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>accountingFrequency</code>: <code><code><a href='#accountingfrequency'>AccountingFrequency</a></code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product category
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>allowSubAllocations</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ProductType`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A classifier for a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)_

```kotlin
enum class ProductType {
    STORAGE,
    COMPUTE,
    INGRESS,
    LICENSE,
    NETWORK_IP,
}
```
For more information, see the individual [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s:

- `STORAGE`: See [`Product.Storage`](/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md) 
- `COMPUTE`: See [`Product.Compute`](/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md) 
- `INGRESS`: See [`Product.Ingress`](/docs/reference/dk.sdu.cloud.accounting.api.Product.Ingress.md) 
- `LICENSE`: See [`Product.License`](/docs/reference/dk.sdu.cloud.accounting.api.Product.License.md) 
- `NETWORK_IP`: See [`Product.NetworkIP`](/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>STORAGE</code> See Product.Storage
</summary>





</details>

<details>
<summary>
<code>COMPUTE</code> See Product.Compute
</summary>





</details>

<details>
<summary>
<code>INGRESS</code> See Product.Ingress
</summary>





</details>

<details>
<summary>
<code>LICENSE</code> See Product.License
</summary>





</details>

<details>
<summary>
<code>NETWORK_IP</code> See Product.NetworkIP
</summary>





</details>



</details>



---

### `ProductV2`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
sealed class ProductV2 {
    abstract val category: ProductCategory
    abstract val description: String
    abstract val hiddenInGrantApplications: Boolean
    abstract val name: String
    abstract val price: Long
    abstract val productType: ProductType
    abstract val usage: Long?

    class Compute : ProductV2()
    class Ingress : ProductV2()
    class License : ProductV2()
    class NetworkIP : ProductV2()
    class Storage : ProductV2()
}
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A short (single-line) description of the Product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Flag to indicate that this Product is not publicly available
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code> Classifier used to explain the type of Product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ProductV2.Compute`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class Compute(
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val description: String?,
    val cpu: Int?,
    val memoryInGigs: Int?,
    val gpu: Int?,
    val cpuModel: String?,
    val memoryModel: String?,
    val gpuModel: String?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val usage: Long?,
    val type: String /* "compute" */,
)
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>





</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>cpu</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>memoryInGigs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>gpu</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>cpuModel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>memoryModel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>gpuModel</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "compute" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `ProductV2.Ingress`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class Ingress(
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val description: String?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val usage: Long?,
    val type: String /* "ingress" */,
)
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>





</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "ingress" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `ProductV2.License`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class License(
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val description: String?,
    val tags: List<String>?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val usage: Long?,
    val type: String /* "license" */,
)
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>





</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "license" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `ProductV2.NetworkIP`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class NetworkIP(
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val description: String?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val usage: Long?,
    val type: String /* "network_ip" */,
)
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>





</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "network_ip" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `ProductV2.Storage`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Products define the services exposed by a Provider._

```kotlin
data class Storage(
    val name: String,
    val price: Long,
    val category: ProductCategory,
    val description: String?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val usage: Long?,
    val type: String /* "storage" */,
)
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>





</details>

<details>
<summary>
<code>price</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Price is for usage of a single product in the accountingFrequency period specified by the product category.
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategory'>ProductCategory</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>usage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "storage" */</code></code> The type discriminator
</summary>





</details>



</details>



---

### `ProductsV2BrowseRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class ProductsV2BrowseRequest(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val filterName: String?,
    val filterProvider: String?,
    val filterProductType: ProductType?,
    val filterCategory: String?,
    val filterUsable: Boolean?,
    val includeBalance: Boolean?,
    val includeMaxBalance: Boolean?,
)
```
Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
semantics of the call:

| Consistency | Description |
|-------------|-------------|
| `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
| `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |

The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
results to be consistent if it contains a complete view at some point in time. In practice this means that the results
must contain all the items, in the correct order and without duplicates.

If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
consistent.

The results might become inconsistent if the client either takes too long, or a service instance goes down while
fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.

---

__📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
paginate through the results.

---

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
</summary>





</details>

<details>
<summary>
<code>next</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A token requesting the next page of items
</summary>





</details>

<details>
<summary>
<code>consistency</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.PaginationRequestV2Consistency.md'>PaginationRequestV2Consistency</a>?</code></code> Controls the consistency guarantees provided by the backend
</summary>





</details>

<details>
<summary>
<code>itemsToSkip</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Items to skip ahead
</summary>





</details>

<details>
<summary>
<code>filterName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductType</code>: <code><code><a href='#producttype'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterUsable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeMaxBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ProductsV2RetrieveRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProductsV2RetrieveRequest(
    val filterName: String,
    val filterCategory: String,
    val filterProvider: String,
    val filterProductType: ProductType?,
    val filterUsable: Boolean?,
    val includeBalance: Boolean?,
    val includeMaxBalance: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>filterCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductType</code>: <code><code><a href='#producttype'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterUsable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeMaxBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

