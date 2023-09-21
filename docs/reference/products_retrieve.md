[UCloud Developer Guide](/docs/developer-guide/README.md) / [Accounting and Project Management](/docs/developer-guide/accounting-and-projects/README.md) / [Products](/docs/developer-guide/accounting-and-projects/products.md)

# Example: Retrieve a single product

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


