[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Software Licenses](/docs/developer-guide/orchestration/compute/license.md)

# Example: Create and configure a license

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

/* In this example we will see how to create and manage a software license */

Licenses.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.License(
            allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
            category = ProductCategoryId(
                id = "example-license", 
                name = "example-license", 
                provider = "example", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            description = "An example license", 
            freeToUse = false, 
            hiddenInGrantApplications = false, 
            name = "example-license", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.LICENSE, 
            tags = emptyList(), 
            unitOfPrice = ProductPriceUnit.PER_UNIT, 
            version = 1, 
            balance = null, 
            id = "example-license", 
            maxUsableBalance = null, 
        ), 
        support = LicenseSupport(
            maintenance = null, 
            product = ProductReference(
                category = "example-license", 
                id = "example-license", 
                provider = "example", 
            ), 
        ), 
    ))), 
)
*/
Licenses.create.call(
    bulkRequestOf(LicenseSpecification(
        product = ProductReference(
            category = "example-license", 
            id = "example-license", 
            provider = "example", 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "5123", 
    )), 
)
*/
Licenses.retrieve.call(
    ResourceRetrieveRequest(
        flags = LicenseIncludeFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeOthers = false, 
            includeProduct = false, 
            includeSupport = false, 
            includeUpdates = false, 
        ), 
        id = "5123", 
    ),
    user
).orThrow()

/*
License(
    createdAt = 1635170395571, 
    id = "5123", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = null, 
    specification = LicenseSpecification(
        product = ProductReference(
            category = "example-license", 
            id = "example-license", 
            provider = "example", 
        ), 
    ), 
    status = LicenseStatus(
        boundTo = emptyList(), 
        resolvedProduct = null, 
        resolvedSupport = null, 
        state = LicenseState.READY, 
    ), 
    updates = emptyList(), 
    providerGeneratedId = "5123", 
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

# In this example we will see how to create and manage a software license

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/licenses/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "balance": null,
#                     "maxUsableBalance": null,
#                     "name": "example-license",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-license",
#                         "provider": "example"
#                     },
#                     "description": "An example license",
#                     "priority": 0,
#                     "tags": [
#                     ],
#                     "version": 1,
#                     "freeToUse": false,
#                     "allowAllocationRequestsFrom": "ALL",
#                     "unitOfPrice": "PER_UNIT",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "LICENSE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "example-license",
#                         "category": "example-license",
#                         "provider": "example"
#                     },
#                     "maintenance": null
#                 }
#             }
#         ]
#     }
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/licenses" -d '{
    "items": [
        {
            "product": {
                "id": "example-license",
                "category": "example-license",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "5123"
#         }
#     ]
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/licenses/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=5123" 

# {
#     "id": "5123",
#     "specification": {
#         "product": {
#             "id": "example-license",
#             "category": "example-license",
#             "provider": "example"
#         }
#     },
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "createdAt": 1635170395571,
#     "status": {
#         "state": "READY",
#         "resolvedSupport": null,
#         "resolvedProduct": null,
#         "boundTo": [
#         ]
#     },
#     "updates": [
#     ],
#     "permissions": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/licenses_simple.png)

</details>


