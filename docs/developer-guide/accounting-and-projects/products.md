<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/providers.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/accounting-and-projects/accounting/wallets.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / Products
# Products

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Products define the services exposed by a Provider._

## Rationale

[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s expose services into UCloud. But, different 
[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s expose different services. UCloud uses [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s to define the 
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

- [`ProductType`](/docs/reference/dk.sdu.cloud.accounting.api.ProductType.md): A classifier for a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md), defines the behavior of a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md).
- [`ProductCategory`](/docs/reference/dk.sdu.cloud.accounting.api.ProductCategory.md): A group of similar [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s. In most cases, [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s in a category
  run on identical hardware. 
- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md): Defines a concrete service exposed by a [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider..md) 

Below, we show an example of how a [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  can organize their services.

![](/backend/accounting-service/wiki/products.png)

__Figure:__ All [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s in UCloud are of a specific type, such as: `STORAGE` and `COMPUTE`.
[`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s have zero or more categories of every type, e.g. `example-slim`. 
In a given category, the [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  has one or more slices.

## Payment Model

UCloud uses a flexible payment model, which allows [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s to use a model which
is familiar to them. In short, a [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  must first choose one of the following payment types:

1. __Differential models__ ([`ChargeType.DIFFERENTIAL_QUOTA`](/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md))
   1. Quota ([`ProductPriceUnit.PER_UNIT`](/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md))
2. __Absolute models__ ([`ChargeType.ABSOLUTE`](/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md))
   1. One-time payment ([`ProductPriceUnit.PER_UNIT`](/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md) and 
      [`ProductPriceUnit.CREDITS_PER_UNIT`](/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md))
   2. Periodic payment ([`ProductPriceUnit.UNITS_PER_X`](/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md) and 
      [`ProductPriceUnit.CREDITS_PER_X`](/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md))
      
---

__📝 NOTE:__ To select a model, you must specify a [`ChargeType`](/docs/reference/dk.sdu.cloud.accounting.api.ChargeType.md)  and a [`ProductPriceUnit`](/docs/reference/dk.sdu.cloud.accounting.api.ProductPriceUnit.md). We have 
shown all valid combinations above.  

---

Quotas put a strict limit on the "number of units" in concurrent use. UCloud measures this number in a 
[`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  specific way. A unit is pre-defined and stable across the entirety of UCloud. A few quick 
examples:

- __Storage:__ Measured in GB (10⁹ bytes. 1 byte = 1 octet)
- __Compute:__ Measured in hyper-threaded cores (vCPU)
- __Public IPs:__ Measured in IP addresses
- __Public links:__ Measured in public links

If using an absolute model, then you must choose the unit of allocation:

- You specify allocations in units (`UNITS_PER_X`). For example: 3000 IP addresses.
- You specify allocations in money (`CREDITS_PER_X`). For example: 1000 DKK.

---

__📝 NOTE:__ For precision purposes, UCloud specifies all money sums as integers. As a result, 1 UCloud credit is equal 
to one millionth of a Danish Crown (DKK).

---

When using periodic payments, you must also specify the length of a single period. [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)s are not required to 
report usage once for every period. But the reporting itself must specify usage in number of periods. 
UCloud supports period lengths of a minute, one hour and one day.

## Understanding the price

All [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s have a `pricePerUnit` property: 

> The `pricePerUnit` specifies the cost of a single _unit_ in a single _period_.
>
> Products not paid with credits must have a `pricePerUnit` of 1. Quotas and one-time payments are always made for a 
> single "period". 

This can lead to some surprising results when defining compute products. Let's consider the example from 
the beginning:

| Name | vCPU | RAM (GB) | GPU | Price |
|------|------|----------|-----|-------|
| `example-slim-1` | 1 | 4 | 0 | 0,100 DKK/hr |
| `example-slim-2` | 2 | 8 | 0 | 0,200 DKK/hr |
| `example-slim-4` | 4 | 16 | 0 | 0,400 DKK/hr |
| `example-slim-8` | 8 | 32 | 0 | 0,800 DKK/hr |

__Table:__ Human-readable compute products.

When implementing this in UCloud, a Provider might naively set the following prices:

| Name | ChargeType | ProductPriceUnit | Price |
|------|------------|------------------|-------|
| `example-slim-1` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-2` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `200_000` |
| `example-slim-4` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `400_000` |
| `example-slim-8` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `800_000` |

__Table:__ ️⚠️ Incorrect implementation of prices in UCloud ️⚠️

__This is wrong.__ UCloud defines the price as the cost of using a single unit in a single period. The "unit of use" 
for a compute product is a single vCPU.  Thus, a correct [`Provider`](/docs/reference/dk.sdu.cloud.provider.api.Provider.md)  implementation would over-report the usage by a 
factor equal to the number of vCPUs in the machine. Instead, the price must be based on a single vCPU:

| Name | ChargeType | ProductPriceUnit | Price |
|------|------------|------------------|-------|
| `example-slim-1` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-2` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-4` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |
| `example-slim-8` | `ABSOLUTE` | `CREDITS_PER_HOUR` | `100_000` |

__Table:__ ✅ Correct implementation of prices in UCloud ✅

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
<td>Contains a unique reference to a [Product](/backend/accounting-service/README.md)</td>
</tr>
<tr>
<td><a href='#producttype'><code>ProductType</code></a></td>
<td>A classifier for a [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)</td>
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

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/products_retrieve.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
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


_Products define the services exposed by a Provider._

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
<code>balance</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Included only with certain endpoints which support `includeBalance`
</summary>





</details>

<details>
<summary>
<code>category</code>: <code><code><a href='#productcategoryid'>ProductCategoryId</a></code></code> The category groups similar products together, it also defines which provider owns the product
</summary>





</details>

<details>
<summary>
<code>chargeType</code>: <code><code><a href='#chargetype'>ChargeType</a></code></code> The category of payment model. Used in combination with unitOfPrice to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A short (single-line) description of the Product
</summary>





</details>

<details>
<summary>
<code>freeToUse</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Indicates that a Wallet is not required to use this Product
</summary>



Under normal circumstances, a `Wallet`  is always required. This is required even if a `Product` 
has a `pricePerUnit` of 0. If `freeToUse = true` then the Wallet requirement is dropped.


</details>

<details>
<summary>
<code>hiddenInGrantApplications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Flag to indicate that this Product is not publicly available
</summary>



⚠️ WARNING: This doesn't make the `Product`  secret. In only hides the `Product`  from the grant
system's UI.


</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

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
<code>priority</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> A integer used for changing the order in which products are displayed (ascending order)
</summary>





</details>

<details>
<summary>
<code>productType</code>: <code><code><a href='#producttype'>ProductType</a></code></code> Classifier used to explain the type of Product
</summary>





</details>

<details>
<summary>
<code>unitOfPrice</code>: <code><code><a href='#productpriceunit'>ProductPriceUnit</a></code></code> The unit of price. Used in combination with chargeType to create a complete payment model.
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> A version number for this Product, managed by UCloud
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>



---

### `Product.Compute`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A version number for this Product, managed by UCloud
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
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A version number for this Product, managed by UCloud
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
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A version number for this Product, managed by UCloud
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
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A version number for this Product, managed by UCloud
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
    val unitOfPrice: ProductPriceUnit?,
    val chargeType: ChargeType?,
    val hiddenInGrantApplications: Boolean?,
    val productType: ProductType,
    val balance: Long?,
    val id: String,
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> A version number for this Product, managed by UCloud
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

