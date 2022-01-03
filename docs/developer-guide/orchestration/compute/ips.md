<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/jobs.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/ingress.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / Public IPs (NetworkIP)
# Public IPs (NetworkIP)

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Network IPs grant users access to an IP address resource._

## Rationale

__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    

IPs are used in combination with [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s. This will attach an IP address to the compute resource. For 
example, on a virtual machine, this might add a new network interface with the IP address.

It is not a strict requirement that the IP address is visible inside the compute environment. However,
it is required that users can access the services exposed by a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  through this API.

If the firewall feature is supported by the provider, then users must define which ports are expected to be
in use by the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md). If the firewall feature is not supported, then all ports must be open by default or
managed from within the compute environment. For example, the firewall feature is not supported if the
firewall is controlled by the virtual machine.

## Table of Contents
<details>
<summary>
<a href='#example-create-and-configure-firewall'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-create-and-configure-firewall'>Create and configure firewall</a></td></tr>
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
<td>Browses the catalog of available resources</td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieve a single resource</td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for all accessible providers</td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td>Searches the catalog of available resources</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates one or more resources</td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td>Deletes one or more resources</td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL attached to a resource</td>
</tr>
<tr>
<td><a href='#updatefirewall'><code>updateFirewall</code></a></td>
<td><i>No description</i></td>
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
<td><a href='#firewallandid'><code>FirewallAndId</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ipprotocol'><code>IPProtocol</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkip'><code>NetworkIP</code></a></td>
<td>A `NetworkIP` for use in `Job`s</td>
</tr>
<tr>
<td><a href='#networkipflags'><code>NetworkIPFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipspecification'><code>NetworkIPSpecification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipspecification.firewall'><code>NetworkIPSpecification.Firewall</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipstate'><code>NetworkIPState</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipstatus'><code>NetworkIPStatus</code></a></td>
<td>The status of an `NetworkIP`</td>
</tr>
<tr>
<td><a href='#networkipsupport'><code>NetworkIPSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipsupport.firewall'><code>NetworkIPSupport.Firewall</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#networkipupdate'><code>NetworkIPUpdate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#portrangeandproto'><code>PortRangeAndProto</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Create and configure firewall
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
        ), 
        support = NetworkIPSupport(
            firewall = NetworkIPSupport.Firewall(
                enabled = true, 
            ), 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example we will see how to create and manage a public IP address */

// Authenticated as user
await callAPI(NetworkipsApi.retrieveProducts(
    {
    }
);

/*
{
    "productsByProvider": {
        "example": [
            {
                "product": {
                    "balance": null,
                    "name": "example-ip",
                    "pricePerUnit": 1,
                    "category": {
                        "name": "example-id",
                        "provider": "example"
                    },
                    "description": "A public IP address",
                    "priority": 0,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "PER_UNIT",
                    "chargeType": "ABSOLUTE",
                    "hiddenInGrantApplications": false,
                    "productType": "NETWORK_IP"
                },
                "support": {
                    "product": {
                        "id": "example-ip",
                        "category": "example-ip",
                        "provider": "example"
                    },
                    "firewall": {
                        "enabled": true
                    }
                }
            }
        ]
    }
}
*/

/* We have a single product available to us. It supports the firewall feature. */

await callAPI(NetworkipsApi.create(
    {
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
    }
);

/*
{
    "responses": [
        {
            "id": "5123"
        }
    ]
}
*/

/* The IP address has been created and has ID 5123 */


/* Updating the firewall causes existing ports to be removed. */

await callAPI(NetworkipsApi.updateFirewall(
    {
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
    }
);

/*
{
}
*/

/* We can read the current state by retrieving the resource */

await callAPI(NetworkipsApi.retrieve(
    {
        "flags": {
            "filterState": null,
            "includeOthers": false,
            "includeUpdates": false,
            "includeSupport": false,
            "includeProduct": false,
            "filterCreatedBy": null,
            "filterCreatedAfter": null,
            "filterCreatedBefore": null,
            "filterProvider": null,
            "filterProductId": null,
            "filterProductCategory": null,
            "filterProviderIds": null,
            "filterIds": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
        },
        "id": "5123"
    }
);

/*
{
    "id": "5123",
    "specification": {
        "product": {
            "id": "example-ip",
            "category": "example-ip",
            "provider": "example"
        },
        "firewall": {
            "openPorts": [
                {
                    "start": 80,
                    "end": 80,
                    "protocol": "TCP"
                }
            ]
        }
    },
    "owner": {
        "createdBy": "user",
        "project": null
    },
    "createdAt": 1635170395571,
    "status": {
        "state": "READY",
        "boundTo": [
        ],
        "ipAddress": null,
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "updates": [
    ],
    "resolvedProduct": null,
    "permissions": null
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

# In this example we will see how to create and manage a public IP address

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/networkips/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "balance": null,
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
#                     }
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



## Remote Procedure Calls

### `browse`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#networkipflags'>NetworkIPFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#networkip'>NetworkIP</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#networkipflags'>NetworkIPFlags</a>&gt;</code>|<code><a href='#networkip'>NetworkIP</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>, <a href='#networkipsupport'>NetworkIPSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `search`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#networkipflags'>NetworkIPFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#networkip'>NetworkIP</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#networkipspecification'>NetworkIPSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateFirewall`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#firewallandid'>FirewallAndId</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FirewallAndId`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FirewallAndId(
    val id: String,
    val firewall: NetworkIPSpecification.Firewall,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>firewall</code>: <code><code><a href='#networkipspecification.firewall'>NetworkIPSpecification.Firewall</a></code></code>
</summary>





</details>



</details>



---

### `IPProtocol`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class IPProtocol {
    TCP,
    UDP,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>TCP</code>
</summary>





</details>

<details>
<summary>
<code>UDP</code>
</summary>





</details>



</details>



---

### `NetworkIP`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `NetworkIP` for use in `Job`s_

```kotlin
data class NetworkIP(
    val id: String,
    val specification: NetworkIPSpecification,
    val owner: ResourceOwner,
    val createdAt: Long,
    val status: NetworkIPStatus,
    val updates: List<NetworkIPUpdate>?,
    val resolvedProduct: Product.NetworkIP?,
    val permissions: ResourcePermissions?,
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
<code>specification</code>: <code><code><a href='#networkipspecification'>NetworkIPSpecification</a></code></code>
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
<code>status</code>: <code><code><a href='#networkipstatus'>NetworkIPStatus</a></code></code> The current status of this resource
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#networkipupdate'>NetworkIPUpdate</a>&gt;?</code></code> A list of updates for this `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>?</code></code>
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
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `NetworkIPFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIPFlags(
    val filterState: NetworkIPState?,
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val includeProduct: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterIds: String?,
    val hideProductId: String?,
    val hideProductCategory: String?,
    val hideProvider: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterState</code>: <code><code><a href='#networkipstate'>NetworkIPState</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
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

<details>
<summary>
<code>hideProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hideProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hideProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPSpecification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIPSpecification(
    val product: ProductReference,
    val firewall: NetworkIPSpecification.Firewall?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> The product used for the `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>firewall</code>: <code><code><a href='#networkipspecification.firewall'>NetworkIPSpecification.Firewall</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPSpecification.Firewall`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Firewall(
    val openPorts: List<PortRangeAndProto>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>openPorts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#portrangeandproto'>PortRangeAndProto</a>&gt;?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPState`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class NetworkIPState {
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
<code>PREPARING</code> A state indicating that the `NetworkIP` is currently being prepared and is expected to reach `READY` soon.
</summary>





</details>

<details>
<summary>
<code>READY</code> A state indicating that the `NetworkIP` is ready for use or already in use.
</summary>





</details>

<details>
<summary>
<code>UNAVAILABLE</code> A state indicating that the `NetworkIP` is currently unavailable.
</summary>



This state can be used to indicate downtime or service interruptions by the provider.


</details>



</details>



---

### `NetworkIPStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The status of an `NetworkIP`_

```kotlin
data class NetworkIPStatus(
    val state: NetworkIPState,
    val boundTo: List<String>?,
    val ipAddress: String?,
    val resolvedSupport: ResolvedSupport<Product.NetworkIP, NetworkIPSupport>?,
    val resolvedProduct: Product.NetworkIP?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#networkipstate'>NetworkIPState</a></code></code>
</summary>





</details>

<details>
<summary>
<code>boundTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The ID of the `Job` that this `NetworkIP` is currently bound to
</summary>





</details>

<details>
<summary>
<code>ipAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The externally accessible IP address allocated to this `NetworkIP`
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>, <a href='#networkipsupport'>NetworkIPSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.NetworkIP.md'>Product.NetworkIP</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `NetworkIPSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIPSupport(
    val product: ProductReference,
    val firewall: NetworkIPSupport.Firewall?,
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

<details>
<summary>
<code>firewall</code>: <code><code><a href='#networkipsupport.firewall'>NetworkIPSupport.Firewall</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPSupport.Firewall`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Firewall(
    val enabled: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `NetworkIPUpdate`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIPUpdate(
    val timestamp: Long?,
    val state: NetworkIPState?,
    val status: String?,
    val changeIpAddress: Boolean?,
    val newIpAddress: String?,
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
<code>state</code>: <code><code><a href='#networkipstate'>NetworkIPState</a>?</code></code> The new state that the `NetworkIP` transitioned to (if any)
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A new status message for the `NetworkIP` (if any)
</summary>





</details>

<details>
<summary>
<code>changeIpAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newIpAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>binding</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobBinding.md'>JobBinding</a>?</code></code>
</summary>





</details>



</details>



---

### `PortRangeAndProto`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class PortRangeAndProto(
    val start: Int,
    val end: Int,
    val protocol: IPProtocol,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>start</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>end</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>protocol</code>: <code><code><a href='#ipprotocol'>IPProtocol</a></code></code>
</summary>





</details>



</details>



---

