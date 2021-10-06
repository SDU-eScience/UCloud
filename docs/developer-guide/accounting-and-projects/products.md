<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/providers.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/accounting/wallets.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / Products
# Products

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#product.compute'><code>Product.Compute</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#product.ingress'><code>Product.Ingress</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#product.license'><code>Product.License</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#product.networkip'><code>Product.NetworkIP</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#product.storage'><code>Product.Storage</code></a></td>
<td><i>No description</i></td>
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
<td>Contains a unique reference to a [Product](/backend/accounting-service/README.md)</td>
</tr>
<tr>
<td><a href='#producttype'><code>ProductType</code></a></td>
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
        category = ProductCategoryId(
            id = "example-compute", 
            name = "example-compute", 
            provider = "example", 
        ), 
        chargeType = ChargeType.ABSOLUTE, 
        cpu = 10, 
        description = "An example compute product", 
        freeToUse = false, 
        gpu = 0, 
        hiddenInGrantApplications = false, 
        memoryInGigs = 20, 
        name = "example-compute", 
        pricePerUnit = 1000000, 
        priority = 0, 
        productType = ProductType.COMPUTE, 
        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
        version = 1, 
        balance = null, 
        id = "example-compute", 
    ), Product.Storage(
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
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(ProductsApi.browse(
    {
        "itemsPerPage": 50,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterName": null,
        "filterProvider": null,
        "filterArea": null,
        "filterCategory": null,
        "filterVersion": null,
        "showAllVersions": null,
        "includeBalance": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "type": "compute",
            "balance": null,
            "name": "example-compute",
            "pricePerUnit": 1000000,
            "category": {
                "name": "example-compute",
                "provider": "example"
            },
            "description": "An example compute product",
            "priority": 0,
            "cpu": 10,
            "memoryInGigs": 20,
            "gpu": 0,
            "version": 1,
            "freeToUse": false,
            "unitOfPrice": "CREDITS_PER_MINUTE",
            "chargeType": "ABSOLUTE",
            "hiddenInGrantApplications": false,
            "productType": "COMPUTE"
        },
        {
            "type": "storage",
            "balance": null,
            "name": "example-storage",
            "pricePerUnit": 1,
            "category": {
                "name": "example-storage",
                "provider": "example"
            },
            "description": "An example storage product (Quota)",
            "priority": 0,
            "version": 1,
            "freeToUse": false,
            "unitOfPrice": "PER_UNIT",
            "chargeType": "DIFFERENTIAL_QUOTA",
            "hiddenInGrantApplications": false,
            "productType": "STORAGE"
        }
    ],
    "next": null
}
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
#             "version": 1,
#             "freeToUse": false,
#             "unitOfPrice": "CREDITS_PER_MINUTE",
#             "chargeType": "ABSOLUTE",
#             "hiddenInGrantApplications": false,
#             "productType": "COMPUTE"
#         },
#         {
#             "type": "storage",
#             "balance": null,
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
        category = ProductCategoryId(
            id = "example-compute", 
            name = "example-compute", 
            provider = "example", 
        ), 
        chargeType = ChargeType.ABSOLUTE, 
        cpu = 10, 
        description = "An example compute product", 
        freeToUse = false, 
        gpu = 0, 
        hiddenInGrantApplications = false, 
        memoryInGigs = 20, 
        name = "example-compute", 
        pricePerUnit = 1000000, 
        priority = 0, 
        productType = ProductType.COMPUTE, 
        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
        version = 1, 
        balance = null, 
        id = "example-compute", 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(ProductsApi.browse(
    {
        "itemsPerPage": 50,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterName": null,
        "filterProvider": null,
        "filterArea": "COMPUTE",
        "filterCategory": null,
        "filterVersion": null,
        "showAllVersions": null,
        "includeBalance": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "type": "compute",
            "balance": null,
            "name": "example-compute",
            "pricePerUnit": 1000000,
            "category": {
                "name": "example-compute",
                "provider": "example"
            },
            "description": "An example compute product",
            "priority": 0,
            "cpu": 10,
            "memoryInGigs": 20,
            "gpu": 0,
            "version": 1,
            "freeToUse": false,
            "unitOfPrice": "CREDITS_PER_MINUTE",
            "chargeType": "ABSOLUTE",
            "hiddenInGrantApplications": false,
            "productType": "COMPUTE"
        }
    ],
    "next": null
}
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
#             "version": 1,
#             "freeToUse": false,
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
    ),
    user
).orThrow()

/*
Product.Compute(
    category = ProductCategoryId(
        id = "example-compute", 
        name = "example-compute", 
        provider = "example", 
    ), 
    chargeType = ChargeType.ABSOLUTE, 
    cpu = 10, 
    description = "An example compute product", 
    freeToUse = false, 
    gpu = 0, 
    hiddenInGrantApplications = false, 
    memoryInGigs = 20, 
    name = "example-compute", 
    pricePerUnit = 1000000, 
    priority = 0, 
    productType = ProductType.COMPUTE, 
    unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
    version = 1, 
    balance = null, 
    id = "example-compute", 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(ProductsApi.retrieve(
    {
        "filterName": "example-compute",
        "filterCategory": "example-compute",
        "filterProvider": "example",
        "filterArea": null,
        "filterVersion": null,
        "includeBalance": null
    }
);

/*
{
    "type": "compute",
    "balance": null,
    "name": "example-compute",
    "pricePerUnit": 1000000,
    "category": {
        "name": "example-compute",
        "provider": "example"
    },
    "description": "An example compute product",
    "priority": 0,
    "cpu": 10,
    "memoryInGigs": 20,
    "gpu": 0,
    "version": 1,
    "freeToUse": false,
    "unitOfPrice": "CREDITS_PER_MINUTE",
    "chargeType": "ABSOLUTE",
    "hiddenInGrantApplications": false,
    "productType": "COMPUTE"
}
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
#     "version": 1,
#     "freeToUse": false,
#     "unitOfPrice": "CREDITS_PER_MINUTE",
#     "chargeType": "ABSOLUTE",
#     "hiddenInGrantApplications": false,
#     "productType": "COMPUTE"
# }

```


</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browse a set of products_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#productsbrowserequest'>ProductsBrowseRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#product'>Product</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint uses the normal pagination and filter mechanisms to return a list of [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  .

__Examples:__

| Example |
|---------|
| [Browse in the full product catalog](/docs/reference/products_browse.md) |
| [Browse for a specific type of product (e.g. compute)](/docs/reference/products_browse-by-type.md) |


### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: SERVICE, ADMIN, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=SERVICE,+ADMIN,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  in UCloud_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#product'>Product</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Only providers and UCloud administrators can create a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  . When this endpoint is
invoked by a provider, then the provider field of the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  must match the invoking user.

The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  will become ready and visible in UCloud immediately after invoking this call.
If no [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  has been created in this category before, then this category will be created.

---

__📝 NOTE:__ Must properties of a [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md)  are immutable and must not be changed.
As a result, you cannot create a new [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  later with different category properties.

---

If the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  already exists, then a new `version` of it is created. Version numbers are
always sequential and the incoming version number is always ignored by UCloud.



## Data Models

### `ChargeType`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class Product {
    abstract val balance: Long?
    abstract val category: ProductCategoryId
    abstract val chargeType: ChargeType
    abstract val description: String
    abstract val freeToUse: Boolean
    abstract val hiddenInGrantApplications: Boolean
    abstract val id: String
    abstract val name: String
    abstract val pricePerUnit: Long
    abstract val priority: Int
    abstract val productType: ProductType
    abstract val unitOfPrice: ProductPriceUnit
    abstract val version: Int

    class Storage : Product()
    class Compute : Product()
    class Ingress : Product()
    class License : Product()
    class NetworkIP : Product()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a></code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `Product.Compute`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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
    val version: Int?,
    val freeToUse: Boolean?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "compute" */,
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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "compute" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Product.Ingress`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Ingress(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "ingress" */,
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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "ingress" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Product.License`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "license" */,
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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "license" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Product.NetworkIP`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIP(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "network_ip" */,
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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "network_ip" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `Product.Storage`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Storage(
    val name: String,
    val pricePerUnit: Long,
    val category: ProductCategoryId,
    val description: String?,
    val priority: Int?,
    val version: Int?,
    val freeToUse: Boolean?,
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
    val type: String /* "storage" */,
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
<code>pricePerUnit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "storage" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ProductCategoryId`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



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

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>



---

### `ProductPriceUnit`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ProductPriceUnit {
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
<code>PER_UNIT</code> Used for resources which either: are charged once for the entire life-time (`ChargeType.ABSOLUTE`) or
</summary>



used to enforce a quota (`ChargeType.DIFFERENTIAL_QUOTA`).

When used in combination with `ChargeType.ABSOLUTE` then this is typically used for resources such as:
licenses, public IPs and links.

When used in combination with `ChargeType.DIFFERENTIAL_QUOTA` it is used to enforce a quota. At any point in
time, a user should never be allowed to use more units than specified in their current `balance`. For example,
if `balance = 100` and `ProductType = ProductType.COMPUTE` then no more than 100 jobs should be allowed to run
at any given point in time. Similarly, this can be used to enforce a storage quota.


</details>

<details>
<summary>
<code>CREDITS_PER_MINUTE</code> Used for resources which are charged periodically in a pre-defined currency.
</summary>



This `ProductPriceUnit` can only be used in combination with `ChargeType.ABSOLUTE`.

The pre-defined currency is decided between the UCloud/Core and the provider out-of-band. UCloud only supports
a single currency for the entire system.

The accounting system only stores balances as an integer, for precision reasons. As a result, a charge using
`CREDITS_PER_X` will always refer to one-millionth of the currency (for example: 1 Credit = 0.000001 DKK).

This price unit comes in several variants: `MINUTE`, `HOUR`, `DAY`. This period is used to define the `units`
of a `charge`. For example, if a `charge` is made on a product with `CREDITS_PER_MINUTE` then the `units`
property refer to the number of minutes which have elapsed. The provider is not required to perform `charge`
operations this often, it only serves to define the true meaning of `units` in the `charge` operation.

The period used SHOULD always refer to monotonically increasing time. In practice, this means that a user should
not be charged differently because of summer/winter time.


</details>

<details>
<summary>
<code>CREDITS_PER_HOUR</code> See `CREDITS_PER_MINUTE`
</summary>





</details>

<details>
<summary>
<code>CREDITS_PER_DAY</code> See `CREDITS_PER_MINUTE`
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_MINUTE</code> Used for resources which are charged periodically.
</summary>



This `ProductPriceUnit` can only be used in combination with `ChargeType.ABSOLUTE`.

All allocations granted to a product of `UNITS_PER_X` specify the amount of units of the recipient can use.
Some examples include:

  - Core hours
  - Public IP hours
  
This price unit comes in several variants: `MINUTE`, `HOUR`, `DAY`. This period is used to define the `units`
of a `charge`. For example, if a `charge` is made on a product with `CREDITS_PER_MINUTE` then the `units`
property refer to the number of minutes which have elapsed. The provider is not required to perform `charge`
operations this often, it only serves to define the true meaning of `units` in the `charge` operation.

The period used SHOULD always refer to monotonically increasing time. In practice, this means that a user should
not be charged differently because of summer/winter time.


</details>

<details>
<summary>
<code>UNITS_PER_HOUR</code> See `UNITS_PER_MINUTE`
</summary>





</details>

<details>
<summary>
<code>UNITS_PER_DAY</code> See `UNITS_PER_MINUTE`
</summary>





</details>



</details>



---

### `ProductReference`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Contains a unique reference to a [Product](/backend/accounting-service/README.md)_

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

### `ProductType`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ProductType {
    STORAGE,
    COMPUTE,
    INGRESS,
    LICENSE,
    NETWORK_IP,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>STORAGE</code>
</summary>





</details>

<details>
<summary>
<code>COMPUTE</code>
</summary>





</details>

<details>
<summary>
<code>INGRESS</code>
</summary>





</details>

<details>
<summary>
<code>LICENSE</code>
</summary>





</details>

<details>
<summary>
<code>NETWORK_IP</code>
</summary>





</details>



</details>



---

### `ProductsBrowseRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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
<code>filterArea</code>: <code><code><a href='#producttype'>ProductType</a>?</code></code>
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



</details>



---

### `ProductsRetrieveRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProductsRetrieveRequest(
    val filterName: String,
    val filterCategory: String,
    val filterProvider: String,
    val filterArea: ProductType?,
    val filterVersion: Int?,
    val includeBalance: Boolean?,
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
<code>filterArea</code>: <code><code><a href='#producttype'>ProductType</a>?</code></code>
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



</details>



---

