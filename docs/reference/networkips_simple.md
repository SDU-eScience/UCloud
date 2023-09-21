[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Public IPs (NetworkIP)](/docs/developer-guide/orchestration/compute/ips.md)

# Example: Create and configure firewall

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

/* In this example we will see how to create and manage a public IP address */

NetworkIPs.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.NetworkIP(
            allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
            category = ProductCategoryId(
                id = "example-id", 
                name = "example-id", 
                provider = "example", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            description = "A public IP address", 
            freeToUse = false, 
            hiddenInGrantApplications = false, 
            name = "example-ip", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.NETWORK_IP, 
            unitOfPrice = ProductPriceUnit.PER_UNIT, 
            version = 1, 
            balance = null, 
            id = "example-ip", 
            maxUsableBalance = null, 
        ), 
        support = NetworkIPSupport(
            firewall = NetworkIPSupport.Firewall(
                enabled = true, 
            ), 
            maintenance = null, 
            product = ProductReference(
                category = "example-ip", 
                id = "example-ip", 
                provider = "example", 
            ), 
        ), 
    ))), 
)
*/

/* We have a single product available to us. It supports the firewall feature. */

NetworkIPs.create.call(
    bulkRequestOf(NetworkIPSpecification(
        firewall = NetworkIPSpecification.Firewall(
            openPorts = listOf(PortRangeAndProto(
                end = 1100, 
                protocol = IPProtocol.TCP, 
                start = 1000, 
            )), 
        ), 
        product = ProductReference(
            category = "example-ip", 
            id = "example-ip", 
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

/* The IP address has been created and has ID 5123 */


/* Updating the firewall causes existing ports to be removed. */

NetworkIPs.updateFirewall.call(
    bulkRequestOf(FirewallAndId(
        firewall = NetworkIPSpecification.Firewall(
            openPorts = listOf(PortRangeAndProto(
                end = 80, 
                protocol = IPProtocol.TCP, 
                start = 80, 
            )), 
        ), 
        id = "5123", 
    )),
    user
).orThrow()

/*
Unit
*/

/* We can read the current state by retrieving the resource */

NetworkIPs.retrieve.call(
    ResourceRetrieveRequest(
        flags = NetworkIPFlags(
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
        id = "5123", 
    ),
    user
).orThrow()

/*
NetworkIP(
    createdAt = 1635170395571, 
    id = "5123", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = null, 
    resolvedProduct = null, 
    specification = NetworkIPSpecification(
        firewall = NetworkIPSpecification.Firewall(
            openPorts = listOf(PortRangeAndProto(
                end = 80, 
                protocol = IPProtocol.TCP, 
                start = 80, 
            )), 
        ), 
        product = ProductReference(
            category = "example-ip", 
            id = "example-ip", 
            provider = "example", 
        ), 
    ), 
    status = NetworkIPStatus(
        boundTo = emptyList(), 
        ipAddress = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        state = NetworkIPState.READY, 
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

# In this example we will see how to create and manage a public IP address

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/networkips/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "balance": null,
#                     "maxUsableBalance": null,
#                     "name": "example-ip",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-id",
#                         "provider": "example"
#                     },
#                     "description": "A public IP address",
#                     "priority": 0,
#                     "version": 1,
#                     "freeToUse": false,
#                     "allowAllocationRequestsFrom": "ALL",
#                     "unitOfPrice": "PER_UNIT",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "NETWORK_IP"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "example-ip",
#                         "category": "example-ip",
#                         "provider": "example"
#                     },
#                     "firewall": {
#                         "enabled": true
#                     },
#                     "maintenance": null
#                 }
#             }
#         ]
#     }
# }

# We have a single product available to us. It supports the firewall feature.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/networkips" -d '{
    "items": [
        {
            "product": {
                "id": "example-ip",
                "category": "example-ip",
                "provider": "example"
            },
            "firewall": {
                "openPorts": [
                    {
                        "start": 1000,
                        "end": 1100,
                        "protocol": "TCP"
                    }
                ]
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

# The IP address has been created and has ID 5123

# Updating the firewall causes existing ports to be removed.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/networkips/firewall" -d '{
    "items": [
        {
            "id": "5123",
            "firewall": {
                "openPorts": [
                    {
                        "start": 80,
                        "end": 80,
                        "protocol": "TCP"
                    }
                ]
            }
        }
    ]
}'


# {
# }

# We can read the current state by retrieving the resource

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/networkips/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=5123" 

# {
#     "id": "5123",
#     "specification": {
#         "product": {
#             "id": "example-ip",
#             "category": "example-ip",
#             "provider": "example"
#         },
#         "firewall": {
#             "openPorts": [
#                 {
#                     "start": 80,
#                     "end": 80,
#                     "protocol": "TCP"
#                 }
#             ]
#         }
#     },
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "createdAt": 1635170395571,
#     "status": {
#         "state": "READY",
#         "boundTo": [
#         ],
#         "ipAddress": null,
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "updates": [
#     ],
#     "resolvedProduct": null,
#     "permissions": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/networkips_simple.png)

</details>


