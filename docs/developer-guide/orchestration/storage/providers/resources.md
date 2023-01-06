<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/metadata/documents.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/providers/control.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / Introduction to Resources API for Providers
# Introduction to Resources API for Providers

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Providers deal almost exclusively with UCloud through resource provider APIs._

## Rationale

We have already told you about the end-user APIs for resources. UCloud uses resources to synchronize work between 
UCloud/Core and the provider. We achieve this synchronization through two different APIs:

- __The ingoing API (Provider)__: This API handles requests, ultimately, from the end-user. UCloud/Core proxies the 
  information from the end-user. During the proxy-step, UCloud/Core performs validation, authentication, authorization 
  and auditing. 
- __The outgoing API (Control):__ The outgoing API is the provider's chance to send requests back to UCloud/Core. 
  For example, we use this API for: auditing, updates and queries about the catalog. 

In this document, we will cover the ingoing API. This API, in most cases, mirrors the end-user API for write 
operations. UCloud expands the API by replacing most request types with a fully-qualified form. This means we replace 
specifications and references with full resource objects.

## A Note on the Examples

The examples in this section follow the same scenario as the end-user API.

## Table of Contents
<details>
<summary>
<a href='#example-creation-of-resources'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-creation-of-resources'>Creation of Resources</a></td></tr>
<tr><td><a href='#example-looking-up-resources-by-provider-generated-id'>Looking up resources by provider generated ID</a></td></tr>
<tr><td><a href='#example-dealing-with-failures'>Dealing with failures</a></td></tr>
<tr><td><a href='#example-dealing-with-partial-failures'>Dealing with partial failures</a></td></tr>
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
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for this providers</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#init'><code>init</code></a></td>
<td>Request from the user to (potentially) initialize any resources</td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Callback received by the Provider when permissions are updated</td>
</tr>
<tr>
<td><a href='#verify'><code>verify</code></a></td>
<td>Invoked by UCloud/Core to trigger verification of a single batch</td>
</tr>
</tbody></table>


</details>

## Example: Creation of Resources
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we show a simple creation request. The creation request is always initiated by a 
user. */

ResourceProvider.create.call(
    bulkRequestOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1234", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "1234", 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(null), 
)
*/

/* In this case, the provider decided not to attach a provider generated ID. */


/* The provider can, at a later point in time, retrieve this resource from UCloud/Core. */

ResourceControl.retrieve.call(
    ResourceRetrieveRequest(
        flags = ExampleResourceFlags(
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
        id = "1234", 
    ),
    provider
).orThrow()

/*
ExampleResource(
    createdAt = 1635170395571, 
    id = "1234", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = ResourcePermissions(
        myself = listOf(Permission.ADMIN), 
        others = emptyList(), 
    ), 
    specification = ExampleResource.Spec(
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        start = 0, 
        target = 100, 
    ), 
    status = ExampleResource.Status(
        resolvedProduct = null, 
        resolvedSupport = null, 
        state = State.RUNNING, 
        value = 10, 
    ), 
    updates = listOf(ExampleResource.Update(
        currentValue = null, 
        newState = State.PENDING, 
        status = "We are about to start counting!", 
        timestamp = 1635170395571, 
    ), ExampleResource.Update(
        currentValue = 10, 
        newState = State.RUNNING, 
        status = "We are now counting!", 
        timestamp = 1635170395571, 
    )), 
    providerGeneratedId = "1234", 
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

# In this example, we show a simple creation request. The creation request is always initiated by a 
# user.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/example" -d '{
    "items": [
        {
            "id": "1234",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        }
    ]
}'


# {
#     "responses": [
#         null
#     ]
# }

# In this case, the provider decided not to attach a provider generated ID.

# The provider can, at a later point in time, retrieve this resource from UCloud/Core.

# Authenticated as provider
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/control/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=1234" 

# {
#     "id": "1234",
#     "specification": {
#         "start": 0,
#         "target": 100,
#         "product": {
#             "id": "example-compute",
#             "category": "example-compute",
#             "provider": "example"
#         }
#     },
#     "createdAt": 1635170395571,
#     "status": {
#         "state": "RUNNING",
#         "value": 10,
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "updates": [
#         {
#             "timestamp": 1635170395571,
#             "status": "We are about to start counting!",
#             "newState": "PENDING",
#             "currentValue": null
#         },
#         {
#             "timestamp": 1635170395571,
#             "status": "We are now counting!",
#             "newState": "RUNNING",
#             "currentValue": 10
#         }
#     ],
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "permissions": {
#         "myself": [
#             "ADMIN"
#         ],
#         "others": [
#         ]
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example.provider.PROVIDERID_simple_create.png)

</details>


## Example: Looking up resources by provider generated ID
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
ResourceProvider.create.call(
    bulkRequestOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1234", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "1234", 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "mhxas1", 
    )), 
)
*/
ResourceControl.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = ExampleResourceFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = "mhxas1", 
            filterState = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeOthers = false, 
            includeProduct = false, 
            includeSupport = false, 
            includeUpdates = false, 
        ), 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
        sortBy = null, 
        sortDirection = SortDirection.ascending, 
    ),
    provider
).orThrow()

/*
PageV2(
    items = listOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1234", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "1234", 
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

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/example" -d '{
    "items": [
        {
            "id": "1234",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "mhxas1"
#         }
#     ]
# }

# Authenticated as provider
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/control/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&filterProviderIds=mhxas1&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "1234",
#             "specification": {
#                 "start": 0,
#                 "target": 100,
#                 "product": {
#                     "id": "example-compute",
#                     "category": "example-compute",
#                     "provider": "example"
#                 }
#             },
#             "createdAt": 1635170395571,
#             "status": {
#                 "state": "RUNNING",
#                 "value": 10,
#                 "resolvedSupport": null,
#                 "resolvedProduct": null
#             },
#             "updates": [
#                 {
#                     "timestamp": 1635170395571,
#                     "status": "We are about to start counting!",
#                     "newState": "PENDING",
#                     "currentValue": null
#                 },
#                 {
#                     "timestamp": 1635170395571,
#                     "status": "We are now counting!",
#                     "newState": "RUNNING",
#                     "currentValue": 10
#                 }
#             ],
#             "owner": {
#                 "createdBy": "user",
#                 "project": null
#             },
#             "permissions": {
#                 "myself": [
#                     "ADMIN"
#                 ],
#                 "others": [
#                 ]
#             }
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

![](/docs/diagrams/example.provider.PROVIDERID_generated_id.png)

</details>


## Example: Dealing with failures
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin
ResourceProvider.create.call(
    bulkRequestOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1234", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "1234", 
    )),
    ucloud
).orThrow()

/*
500 Internal Server Error
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

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/example" -d '{
    "items": [
        {
            "id": "1234",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        }
    ]
}'


# 500 Internal Server Error

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example.provider.PROVIDERID_create_failure.png)

</details>


## Example: Dealing with partial failures
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>The UCloud/Core service user (<code>ucloud</code>)</li>
<li>The provider (<code>provider</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we will discover how a provider should deal with a partial failure. */

ResourceProvider.create.call(
    bulkRequestOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1234", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "1234", 
    ), ExampleResource(
        createdAt = 1635170395571, 
        id = "51214", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = ExampleResource.Spec(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            start = 0, 
            target = 100, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = listOf(ExampleResource.Update(
            currentValue = null, 
            newState = State.PENDING, 
            status = "We are about to start counting!", 
            timestamp = 1635170395571, 
        ), ExampleResource.Update(
            currentValue = 10, 
            newState = State.RUNNING, 
            status = "We are now counting!", 
            timestamp = 1635170395571, 
        )), 
        providerGeneratedId = "51214", 
    )),
    ucloud
).orThrow()

/*
500 Internal Server Error
*/

/* In this case, imagine that the provider failed to create the second resource. This should
immediately trigger cleanup on the provider, if the first resource was already created. The provider
should then respond with an appropriate error message. Providers should not attempt to only
partially create the resources. */

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

# In this example, we will discover how a provider should deal with a partial failure.

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/example" -d '{
    "items": [
        {
            "id": "1234",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        },
        {
            "id": "51214",
            "specification": {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            },
            "createdAt": 1635170395571,
            "status": {
                "state": "RUNNING",
                "value": 10,
                "resolvedSupport": null,
                "resolvedProduct": null
            },
            "updates": [
                {
                    "timestamp": 1635170395571,
                    "status": "We are about to start counting!",
                    "newState": "PENDING",
                    "currentValue": null
                },
                {
                    "timestamp": 1635170395571,
                    "status": "We are now counting!",
                    "newState": "RUNNING",
                    "currentValue": 10
                }
            ],
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "permissions": {
                "myself": [
                    "ADMIN"
                ],
                "others": [
                ]
            }
        }
    ]
}'


# 500 Internal Server Error

# In this case, imagine that the provider failed to create the second resource. This should
# immediately trigger cleanup on the provider, if the first resource was already created. The provider
# should then respond with an appropriate error message. Providers should not attempt to only
# partially create the resources.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example.provider.PROVIDERID_create_failure.png)

</details>



## Remote Procedure Calls

### `retrieveProducts`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for this providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ExampleResourceSupport.md'>ExampleResourceSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint responds with the [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s supported by
this provider along with details for how [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)  is
supported. The [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md)s must be registered with
UCloud/Core already.


### `create`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ExampleResource.md'>ExampleResource</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ExampleResource.md'>ExampleResource</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `init`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request from the user to (potentially) initialize any resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceInitializationRequest.md'>ResourceInitializationRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request is sent by the client, if the client believes that initialization of resources 
might be needed. NOTE: This request might be sent even if initialization has already taken 
place. UCloud/Core does not check if initialization has already taken place, it simply validates
the request.


### `updateAcl`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Callback received by the Provider when permissions are updated_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAclWithResource.md'>UpdatedAclWithResource</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ExampleResource.md'>ExampleResource</a>&gt;&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
internal state, then they may simply ignore this request by responding with `200 OK`. The
Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
not acknowledge the request.


### `verify`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Invoked by UCloud/Core to trigger verification of a single batch_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.ExampleResource.md'>ExampleResource</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
Provider should immediately determine if these are still valid and recognized by the Provider.
If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
an update for each affected resource.


