[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# Example: Browse all available products

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
#             "cpuModel": null,
#             "memoryModel": null,
#             "gpuModel": null,
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


