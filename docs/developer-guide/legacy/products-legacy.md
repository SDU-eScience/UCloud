<p align='center'>
<a href='/docs/developer-guide/legacy/projects-legacy/favorites.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Legacy](/docs/developer-guide/legacy/README.md) / Products (Legacy)
# Products (Legacy)

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


## Table of Contents
<details>
<summary>
<a href='#example-browse-all-available-products'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-browse-all-available-products'>Browse all available products</a></td></tr>
<tr><td><a href='#example-browse-products-by-type'>Browse products by type</a></td></tr>
<tr><td><a href='#example-retrieve-a-single-product'>Retrieve a single product</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
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
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#chargetype'><code>ChargeType</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#product'><code>Product</code></a></td>
<td>Products define the services exposed by a Provider.</td>
</tr>
<tr>
<td><a href='#product.compute'><code>Product.Compute</code></a></td>
<td>A compute Product</td>
</tr>
<tr>
<td><a href='#product.ingress'><code>Product.Ingress</code></a></td>
<td>An ingress Product</td>
</tr>
<tr>
<td><a href='#product.license'><code>Product.License</code></a></td>
<td>A license Product</td>
</tr>
<tr>
<td><a href='#product.networkip'><code>Product.NetworkIP</code></a></td>
<td>An IP address Product</td>
</tr>
<tr>
<td><a href='#product.storage'><code>Product.Storage</code></a></td>
<td>A storage Product</td>
</tr>
<tr>
<td><a href='#productcategoryid'><code>ProductCategoryId</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#productpriceunit'><code>ProductPriceUnit</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#productreference'><code>ProductReference</code></a></td>
<td>Contains a unique reference to a Product</td>
</tr>
<tr>
<td><a href='#allocationrequestsgroup'><code>AllocationRequestsGroup</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#productsbrowserequest'><code>ProductsBrowseRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#productsretrieverequest'><code>ProductsRetrieveRequest</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Browse all available products
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Products.browse.call(
    ProductsBrowseRequest(
        consistency = null, 
        filterArea = null, 
        filterCategory = null, 
        filterName = null, 
        filterProvider = null, 
        filterVersion = null, 
        includeBalance = null, 
        includeMaxBalance = null, 
        itemsPerPage = 50, 
        itemsToSkip = null, 
        next = null, 
        showAllVersions = null, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(Product.Compute(
        allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
        category = ProductCategoryId(
            id = "example-compute", 
            name = "example-compute", 
            provider = "example", 
        ), 
        chargeType = ChargeType.ABSOLUTE, 
        cpu = 10, 
        cpuModel = null, 
        description = "An example compute product", 
        freeToUse = false, 
        gpu = 0, 
        gpuModel = null, 
        hiddenInGrantApplications = false, 
        memoryInGigs = 20, 
        memoryModel = null, 
        name = "example-compute", 
        pricePerUnit = 1000000, 
        priority = 0, 
        productType = ProductType.COMPUTE, 
        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
        version = 1, 
        balance = null, 
        id = "example-compute", 
        maxUsableBalance = null, 
    ), Product.Storage(
        allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
        category = ProductCategoryId(
            id = "example-storage", 
            name = "example-storage", 
            provider = "example", 
        ), 
        chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
        description = "An example storage product (Quota)", 
        freeToUse = false, 
        hiddenInGrantApplications = false, 
        name = "example-storage", 
        pricePerUnit = 1, 
        priority = 0, 
        productType = ProductType.STORAGE, 
        unitOfPrice = ProductPriceUnit.PER_UNIT, 
        version = 1, 
        balance = null, 
        id = "example-storage", 
        maxUsableBalance = null, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/products/browse?itemsPerPage=50" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "type": "compute",
#             "balance": null,
#             "maxUsableBalance": null,
#             "name": "example-compute",
#             "pricePerUnit": 1000000,
#             "category": {
#                 "name": "example-compute",
#                 "provider": "example"
#             },
#             "description": "An example compute product",
#             "priority": 0,
#             "cpu": 10,
#             "memoryInGigs": 20,
#             "gpu": 0,
#             "cpuModel": null,
#             "memoryModel": null,
#             "gpuModel": null,
#             "version": 1,
#             "freeToUse": false,
#             "allowAllocationRequestsFrom": "ALL",
#             "unitOfPrice": "CREDITS_PER_MINUTE",
#             "chargeType": "ABSOLUTE",
#             "hiddenInGrantApplications": false,
#             "productType": "COMPUTE"
#         },
#         {
#             "type": "storage",
#             "balance": null,
#             "maxUsableBalance": null,
#             "name": "example-storage",
#             "pricePerUnit": 1,
#             "category": {
#                 "name": "example-storage",
#                 "provider": "example"
#             },
#             "description": "An example storage product (Quota)",
#             "priority": 0,
#             "version": 1,
#             "freeToUse": false,
#             "allowAllocationRequestsFrom": "ALL",
#             "unitOfPrice": "PER_UNIT",
#             "chargeType": "DIFFERENTIAL_QUOTA",
#             "hiddenInGrantApplications": false,
#             "productType": "STORAGE"
#         }
#     ],
#     "next": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/products_browse.png)

</details>


## Example: Browse products by type
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Products.browse.call(
    ProductsBrowseRequest(
        consistency = null, 
        filterArea = ProductType.COMPUTE, 
        filterCategory = null, 
        filterName = null, 
        filterProvider = null, 
        filterVersion = null, 
        includeBalance = null, 
        includeMaxBalance = null, 
        itemsPerPage = 50, 
        itemsToSkip = null, 
        next = null, 
        showAllVersions = null, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(Product.Compute(
        allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
        category = ProductCategoryId(
            id = "example-compute", 
            name = "example-compute", 
            provider = "example", 
        ), 
        chargeType = ChargeType.ABSOLUTE, 
        cpu = 10, 
        cpuModel = null, 
        description = "An example compute product", 
        freeToUse = false, 
        gpu = 0, 
        gpuModel = null, 
        hiddenInGrantApplications = false, 
        memoryInGigs = 20, 
        memoryModel = null, 
        name = "example-compute", 
        pricePerUnit = 1000000, 
        priority = 0, 
        productType = ProductType.COMPUTE, 
        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
        version = 1, 
        balance = null, 
        id = "example-compute", 
        maxUsableBalance = null, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/products/browse?itemsPerPage=50&filterArea=COMPUTE" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "type": "compute",
#             "balance": null,
#             "maxUsableBalance": null,
#             "name": "example-compute",
#             "pricePerUnit": 1000000,
#             "category": {
#                 "name": "example-compute",
#                 "provider": "example"
#             },
#             "description": "An example compute product",
#             "priority": 0,
#             "cpu": 10,
#             "memoryInGigs": 20,
#             "gpu": 0,
#             "cpuModel": null,
#             "memoryModel": null,
#             "gpuModel": null,
#             "version": 1,
#             "freeToUse": false,
#             "allowAllocationRequestsFrom": "ALL",
#             "unitOfPrice": "CREDITS_PER_MINUTE",
#             "chargeType": "ABSOLUTE",
#             "hiddenInGrantApplications": false,
#             "productType": "COMPUTE"
#         }
#     ],
#     "next": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/products_browse-by-type.png)

</details>


## Example: Retrieve a single product
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>An authenticated user (<code>user</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
Products.retrieve.call(
    ProductsRetrieveRequest(
        filterArea = null, 
        filterCategory = "example-compute", 
        filterName = "example-compute", 
        filterProvider = "example", 
        filterVersion = null, 
        includeBalance = null, 
        includeMaxBalance = null, 
    ),
    user
).orThrow()

/*
Product.Compute(
    allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
    category = ProductCategoryId(
        id = "example-compute", 
        name = "example-compute", 
        provider = "example", 
    ), 
    chargeType = ChargeType.ABSOLUTE, 
    cpu = 10, 
    cpuModel = null, 
    description = "An example compute product", 
    freeToUse = false, 
    gpu = 0, 
    gpuModel = null, 
    hiddenInGrantApplications = false, 
    memoryInGigs = 20, 
    memoryModel = null, 
    name = "example-compute", 
    pricePerUnit = 1000000, 
    priority = 0, 
    productType = ProductType.COMPUTE, 
    unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
    version = 1, 
    balance = null, 
    id = "example-compute", 
    maxUsableBalance = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/products/retrieve?filterName=example-compute&filterCategory=example-compute&filterProvider=example" 

# {
#     "type": "compute",
#     "balance": null,
#     "maxUsableBalance": null,
#     "name": "example-compute",
#     "pricePerUnit": 1000000,
#     "category": {
#         "name": "example-compute",
#         "provider": "example"
#     },
#     "description": "An example compute product",
#     "priority": 0,
#     "cpu": 10,
#     "memoryInGigs": 20,
#     "gpu": 0,
#     "cpuModel": null,
#     "memoryModel": null,
#     "gpuModel": null,
#     "version": 1,
#     "freeToUse": false,
#     "allowAllocationRequestsFrom": "ALL",
#     "unitOfPrice": "CREDITS_PER_MINUTE",
#     "chargeType": "ABSOLUTE",
#     "hiddenInGrantApplications": false,
#     "productType": "COMPUTE"
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/products_retrieve.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browse a set of products_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#productsbrowserequest'>ProductsBrowseRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#product'>Product</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint uses the normal pagination and filter mechanisms to return a list of [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md).

__Examples:__

| Example |
|---------|
| [Browse in the full product catalog](/docs/reference/products_browse.md) |
| [Browse for a specific type of product (e.g. compute)](/docs/reference/products_browse-by-type.md) |


### `retrieve`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single product_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#productsretrieverequest'>ProductsRetrieveRequest</a></code>|<code><a href='#product'>Product</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|


__Examples:__

| Example |
|---------|
| [Retrieving a single product by ID](/docs/reference/products_retrieve.md) |


### `create`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: SERVICE, ADMIN, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=SERVICE,+ADMIN,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  in UCloud_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#product'>Product</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only providers and UCloud administrators can create a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md). When this endpoint is
invoked by a provider, then the provider field of the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  must match the invoking user.

The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  will become ready and visible in UCloud immediately after invoking this call.
If no [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  has been created in this category before, then this category will be created.

---

__📝 NOTE:__ Most properties of a [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md)  are immutable and must not be changed.
As a result, you cannot create a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  later with different category properties.

---

If the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  already exists, then a new `version` of it is created. Version numbers are
always sequential and the incoming version number is always ignored by UCloud.



## Data Models

### `ChargeType`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


```kotlin
enum class ChargeType {
    ABSOLUTE,
    DIFFERENTIAL_QUOTA,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ABSOLUTE</code>
</summary>





</details>

<details>
<summary>
<code>DIFFERENTIAL_QUOTA</code>
</summary>





</details>



</details>



---

### `Product`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Products define the services exposed by a Provider._

```kotlin
sealed class Product {
    abstract val allowAllocationRequestsFrom: AllocationRequestsGroup
    abstract val balance: Long?
    abstract val category: ProductCategoryId
    abstract val chargeType: ChargeType
    abstract val description: String
    abstract val freeToUse: Boolean
    abstract val hiddenInGrantApplications: Boolean
    abstract val id: String
    abstract val maxUsableBalance: Long?
    abstract val name: String
    abstract val pricePerUnit: Long
    abstract val priority: Int
    abstract val productType: ProductType
    abstract val unitOfPrice: ProductPriceUnit
    abstract val version: Int

    class Compute : Product()
    class Ingress : Product()
    class License : Product()
    class NetworkIP : Product()
    class Storage : Product()
}
```
For more information see [this](/docs/developer-guide/accounting-and-projects/products.md) page.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a></code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a></code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Indicates that a Wallet is not required to use this Product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


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
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique name associated with this Product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code> Classifier used to explain the type of Product
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a></code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> This property is no longer used.
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>



---

### `Product.Compute`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_A compute Product_

```kotlin
data class Compute(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val cpu: Int?,
    val memoryInGigs: Int?,
    val gpu: Int?,
    val cpuModel: String?,
    val memoryModel: String?,
    val gpuModel: String?,
    val version: Int?,
    val freeToUse: Boolean?,
    val allowAllocationRequestsFrom: AllocationRequestsGroup?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val maxUsableBalance: Long?,
    val type: String /* "compute" */,
)
```
| Unit | API |
|------|-----|
| Measured in hyper-threaded cores (vCPU) | [Click here](/docs/developer-guide/orchestration/compute/jobs.md) |

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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>



For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A integer used for changing the order in which products are displayed (ascending order)
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> This property is no longer used.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a>?</code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>



category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
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

### `Product.Ingress`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_An ingress Product_

```kotlin
data class Ingress(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val allowAllocationRequestsFrom: AllocationRequestsGroup?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val maxUsableBalance: Long?,
    val type: String /* "ingress" */,
)
```
| Unit | API |
|------|-----|
| Measured in number of ingresses | [Click here](/docs/developer-guide/orchestration/compute/ingress.md) |

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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>



For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> This property is no longer used.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a>?</code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>



category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
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

### `Product.License`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_A license Product_

```kotlin
data class License(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val tags: List<String>?,
    val version: Int?,
    val freeToUse: Boolean?,
    val allowAllocationRequestsFrom: AllocationRequestsGroup?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val maxUsableBalance: Long?,
    val type: String /* "license" */,
)
```
| Unit | API |
|------|-----|
| Measured in number of licenses | [Click here](/docs/developer-guide/orchestration/compute/license.md) |

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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>



For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>





</details>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> This property is no longer used.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a>?</code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>



category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
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

### `Product.NetworkIP`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_An IP address Product_

```kotlin
data class NetworkIP(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val allowAllocationRequestsFrom: AllocationRequestsGroup?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val maxUsableBalance: Long?,
    val type: String /* "network_ip" */,
)
```
| Unit | API |
|------|-----|
| Measured in number of IP addresses | [Click here](/docs/developer-guide/orchestration/compute/ips.md) |

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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>



For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> This property is no longer used.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a>?</code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>



category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
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

### `Product.Storage`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_A storage Product_

```kotlin
data class Storage(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val allowAllocationRequestsFrom: AllocationRequestsGroup?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val maxUsableBalance: Long?,
    val type: String /* "storage" */,
)
```
| Unit | API |
|------|-----|
| Measured in GB (10⁹ bytes. 1 byte = 1 octet) | [Click here](/docs/developer-guide/orchestration/storage/files.md) |

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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> The price of a single unit in a single period
</summary>



For more information go 
[here](/docs/developer-guide/accounting-and-projects/products.md#understanding-the-price).


</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> This property is no longer used.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>allowAllocationRequestsFrom</code>: <code><code><a href='#allocationrequestsgroup'>AllocationRequestsGroup</a>?</code></code> Indicates who should be able to make allocation requests for this product (more specifically the product
</summary>



category).

Possible options are:
 - `ALL` (default): Allows allocation requests from both projects and personal workspaces,
 - `PROJECTS`: Allow allocation requests from projects, but not from personal workspaces,
 - `PERSONAL`: Allow allocation requests from personal workspaces, but not projects.


</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
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
<code>productType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>maxUsableBalance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeMaxBalance`
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

### `ProductCategoryId`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


```kotlin
data class ProductCategoryId(
    val name: String,
    val provider: String,
    val id: String,
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
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>



---

### `ProductPriceUnit`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


```kotlin
enum class ProductPriceUnit {
    CREDITS_PER_UNIT,
    PER_UNIT,
    CREDITS_PER_MINUTE,
    CREDITS_PER_HOUR,
    CREDITS_PER_DAY,
    UNITS_PER_MINUTE,
    UNITS_PER_HOUR,
    UNITS_PER_DAY,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>CREDITS_PER_UNIT</code>
</summary>





</details>

<details>
<summary>
<code>PER_UNIT</code>
</summary>





</details>

<details>
<summary>
<code>CREDITS_PER_MINUTE</code>
</summary>





</details>

<details>
<summary>
<code>CREDITS_PER_HOUR</code>
</summary>





</details>

<details>
<summary>
<code>CREDITS_PER_DAY</code>
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_MINUTE</code>
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_HOUR</code>
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_DAY</code>
</summary>





</details>



</details>



---

### `ProductReference`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Contains a unique reference to a Product_

```kotlin
data class ProductReference(
    val id: String,
    val category: String,
    val provider: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The `Product` ID
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the `Product`'s category
</summary>





</details>

<details>
<summary>
<code>provider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The provider of the `Product`
</summary>





</details>



</details>



---

### `AllocationRequestsGroup`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class AllocationRequestsGroup {
    ALL,
    PERSONAL,
    PROJECT,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ALL</code>
</summary>





</details>

<details>
<summary>
<code>PERSONAL</code>
</summary>





</details>

<details>
<summary>
<code>PROJECT</code>
</summary>





</details>



</details>



---

### `ProductsBrowseRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class ProductsBrowseRequest(
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val filterName: String?,
    val filterProvider: String?,
    val filterArea: ProductType?,
    val filterCategory: String?,
    val filterVersion: Int?,
    val showAllVersions: Boolean?,
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
<code>filterArea</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>showAllVersions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
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

### `ProductsRetrieveRequest`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProductsRetrieveRequest(
    val filterName: String,
    val filterCategory: String,
    val filterProvider: String,
    val filterArea: ProductType?,
    val filterVersion: Int?,
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
<code>filterArea</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md'>ProductType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
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

