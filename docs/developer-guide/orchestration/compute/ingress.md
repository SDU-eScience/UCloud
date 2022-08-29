<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/ips.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/license.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / Public Links (Ingress)
# Public Links (Ingress)

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Ingresses provide a way to attach custom links to interactive web-interfaces._

## Rationale

__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    

When an interactive (web) application runs, it typically uses a provider generated URL. The ingress feature
allows providers to give access to these [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s through a custom URL.

## Table of Contents
<details>
<summary>
<a href='#example-create-and-configure-an-ingress'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-create-and-configure-an-ingress'>Create and configure an Ingress</a></td></tr>
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
<td><a href='#init'><code>init</code></a></td>
<td>Request (potential) initialization of resources</td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL attached to a resource</td>
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
<td><a href='#ingressincludeflags'><code>IngressIncludeFlags</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Create and configure an Ingress
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
        ), 
        support = IngressSupport(
            domainPrefix = "app-", 
            domainSuffix = ".example.com", 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will see how to create and manage an ingress */

// Authenticated as user
await callAPI(IngressesApi.retrieveProducts(
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
                    "name": "example-ingress",
                    "pricePerUnit": 1,
                    "category": {
                        "name": "example-ingress",
                        "provider": "example-ingress"
                    },
                    "description": "An example ingress",
                    "priority": 0,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "PER_UNIT",
                    "chargeType": "ABSOLUTE",
                    "hiddenInGrantApplications": false,
                    "productType": "INGRESS"
                },
                "support": {
                    "domainPrefix": "app-",
                    "domainSuffix": ".example.com",
                    "product": {
                        "id": "example-ingress",
                        "category": "example-ingress",
                        "provider": "example"
                    }
                }
            }
        ]
    }
}
*/

/* We have a single product available. This product requires that all ingresses start with "app-" and 
ends with ".example.com" */


/* 📝 NOTE: Providers can perform additional validation. For example, must providers won't support 
arbitrary levels of sub-domains. That is, must providers would reject the value 
app-this.is.not.what.we.want.example.com. */

await callAPI(IngressesApi.create(
    {
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
    }
);

/*
{
    "responses": [
        {
            "id": "5127"
        }
    ]
}
*/
await callAPI(IngressesApi.retrieve(
    {
        "flags": {
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
            "filterState": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
        },
        "id": "5127"
    }
);

/*
{
    "id": "5127",
    "specification": {
        "domain": "app-mylink.example.com",
        "product": {
            "id": "example-ingress",
            "category": "example-ingress",
            "provider": "example"
        }
    },
    "owner": {
        "createdBy": "user",
        "project": null
    },
    "createdAt": 1635170395571,
    "status": {
        "boundTo": [
        ],
        "state": "READY",
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "updates": [
    ],
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

# In this example, we will see how to create and manage an ingress

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/ingresses/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "balance": null,
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
#                     }
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



## Remote Procedure Calls

### `browse`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#ingressincludeflags'>IngressIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Ingress.md'>Ingress</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#ingressincludeflags'>IngressIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Ingress.md'>Ingress</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Ingress.md'>Product.Ingress</a>, <a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.IngressSupport.md'>IngressSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

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
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#ingressincludeflags'>IngressIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Ingress.md'>Ingress</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.IngressSpecification.md'>IngressSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `init`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request (potential) initialization of resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request is sent by the client, if the client believes that initialization of resources 
might be needed. NOTE: This request might be sent even if initialization has already taken 
place. UCloud/Core does not check if initialization has already taken place, it simply validates
the request.


### `updateAcl`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `IngressIncludeFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IngressIncludeFlags(
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
    val filterState: IngressState?,
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
<code>filterState</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.IngressState.md'>IngressState</a>?</code></code>
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

