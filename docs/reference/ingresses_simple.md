[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Public Links (Ingress)](/docs/developer-guide/orchestration/compute/ingress.md)

# Example: Create and configure an Ingress

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

/* In this example, we will see how to create and manage an ingress */

Ingresses.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.Ingress(
            allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
            category = ProductCategoryId(
                id = "example-ingress", 
                name = "example-ingress", 
                provider = "example-ingress", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            description = "An example ingress", 
            freeToUse = false, 
            hiddenInGrantApplications = false, 
            name = "example-ingress", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.INGRESS, 
            unitOfPrice = ProductPriceUnit.PER_UNIT, 
            version = 1, 
            balance = null, 
            id = "example-ingress", 
            maxUsableBalance = null, 
        ), 
        support = IngressSupport(
            domainPrefix = "app-", 
            domainSuffix = ".example.com", 
            maintenance = null, 
            product = ProductReference(
                category = "example-ingress", 
                id = "example-ingress", 
                provider = "example", 
            ), 
        ), 
    ))), 
)
*/

/* We have a single product available. This product requires that all ingresses start with "app-" and 
ends with ".example.com" */


/* 📝 NOTE: Providers can perform additional validation. For example, must providers won't support 
arbitrary levels of sub-domains. That is, must providers would reject the value 
app-this.is.not.what.we.want.example.com. */

Ingresses.create.call(
    bulkRequestOf(IngressSpecification(
        domain = "app-mylink.example.com", 
        product = ProductReference(
            category = "example-ingress", 
            id = "example-ingress", 
            provider = "example", 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "5127", 
    )), 
)
*/
Ingresses.retrieve.call(
    ResourceRetrieveRequest(
        flags = IngressIncludeFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            filterState = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeOthers = false, 
            includeProduct = false, 
            includeSupport = false, 
            includeUpdates = false, 
        ), 
        id = "5127", 
    ),
    user
).orThrow()

/*
Ingress(
    createdAt = 1635170395571, 
    id = "5127", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = null, 
    specification = IngressSpecification(
        domain = "app-mylink.example.com", 
        product = ProductReference(
            category = "example-ingress", 
            id = "example-ingress", 
            provider = "example", 
        ), 
    ), 
    status = IngressStatus(
        boundTo = emptyList(), 
        resolvedProduct = null, 
        resolvedSupport = null, 
        state = IngressState.READY, 
    ), 
    updates = emptyList(), 
    providerGeneratedId = "5127", 
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

# In this example, we will see how to create and manage an ingress

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/ingresses/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "balance": null,
#                     "maxUsableBalance": null,
#                     "name": "example-ingress",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-ingress",
#                         "provider": "example-ingress"
#                     },
#                     "description": "An example ingress",
#                     "priority": 0,
#                     "version": 1,
#                     "freeToUse": false,
#                     "allowAllocationRequestsFrom": "ALL",
#                     "unitOfPrice": "PER_UNIT",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "INGRESS"
#                 },
#                 "support": {
#                     "domainPrefix": "app-",
#                     "domainSuffix": ".example.com",
#                     "product": {
#                         "id": "example-ingress",
#                         "category": "example-ingress",
#                         "provider": "example"
#                     },
#                     "maintenance": null
#                 }
#             }
#         ]
#     }
# }

# We have a single product available. This product requires that all ingresses start with "app-" and 
# ends with ".example.com"

# 📝 NOTE: Providers can perform additional validation. For example, must providers won't support 
# arbitrary levels of sub-domains. That is, must providers would reject the value 
# app-this.is.not.what.we.want.example.com.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/ingresses" -d '{
    "items": [
        {
            "domain": "app-mylink.example.com",
            "product": {
                "id": "example-ingress",
                "category": "example-ingress",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "5127"
#         }
#     ]
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/ingresses/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=5127" 

# {
#     "id": "5127",
#     "specification": {
#         "domain": "app-mylink.example.com",
#         "product": {
#             "id": "example-ingress",
#             "category": "example-ingress",
#             "provider": "example"
#         }
#     },
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "createdAt": 1635170395571,
#     "status": {
#         "boundTo": [
#         ],
#         "state": "READY",
#         "resolvedSupport": null,
#         "resolvedProduct": null
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

![](/docs/diagrams/ingresses_simple.png)

</details>


