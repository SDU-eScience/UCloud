<p align='center'>
<a href='/docs/developer-guide/accounting-and-projects/grants/gifts.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/filecollections.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / Introduction to Resources
# Introduction to Resources

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Resources are the base abstraction used for orchestration in UCloud._

## Rationale

In this article, we will take a closer look at what we mean when we say that UCloud is an orchestrator of 
resources. Before you begin, we recommend that you have already read about:

- [Providers](/docs/developer-guide/accounting-and-projects/providers.md): Exposes compute and storage 
  resources to end-users.
- [Products](/docs/developer-guide/accounting-and-projects/products.md): Defines the services exposed by 
  providers.
- [Wallets](/docs/developer-guide/accounting-and-projects/accounting/wallets.md): Holds allocations which 
  grant access to products.

UCloud uses the resource abstraction to synchronize tasks between UCloud/Core and providers. As a result, 
resources are often used to describe work for the provider. For example, a computational [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is one type 
of resource used in UCloud. 

To understand how resources work, we will first examine what all resources have in common:

- __A set of unique identifiers:__ Users and services can reference resources by using a unique ID.
- __Product and provider reference:__ Most resources describe a work of a provider. As a result, these 
  resources must have a backing product.
- __A resource specification:__ Describes the resource. For example, this could be the parameters of a 
  computational [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job..md) 
- __Ownership and permissions:__ All resources have exactly one 
  [workspace](/docs/developer-guide/accounting-and-projects/projects/projects.md) owner.
- __Updates and status:__ Providers can send regular updates about a resource. These update describe 
  changes in the system. These changes in turn affect the current status.
  
## The Catalog

UCloud, in almost all cases, store a record of all resources in use. We refer to this datastore as the 
catalog of UCloud. As a result, UCloud/Core can fulfil some operations without involving the provider. 
In particular, UCloud/Core performs many read operations without the provider's involvement.

End-users interact with all resources through a standarized API. The API provides common CRUD operations 
along with permission related operations. Concrete resources further extend this API with resource specific 
tasks. For example, virtual machines expose an operation to shut down the machine. 

On this page we will discuss the end-user API. But on the following pages, you can discover the siblings of 
this API used by providers:

- UCloud/Core invokes the Provider API to proxy information from the end-user API
- The provider invokes the Control API to register changes in UCloud/Core

## The Permission Model

UCloud uses a RBAC based permission model. As a workspace administrator, you must assign permissions for 
resources to workspace groups.

Currently, UCloud has the following permissions:

- `READ`: Grants access to operations which return a resource
- `EDIT`:  Grants access to operations which modify a resource
- `ADMIN`: Grants access to privileged operations which read or modify a resource. Workspace administrators
  hold this permission. It is not possible to grant this permission through [`example.updateAcl`](/docs/reference/example.updateAcl.md).
- `PROVIDER`: Grants access to privileged operations which read or modify a resource. Implicit permission 
  granted to providers of a resource. It is not possible to grant this permission through
  [`example.updateAcl`](/docs/reference/example.updateAcl.md).
  
UCloud/Core checks all permissions before proxying information to the provider. However, this doesn't mean 
that an operation must succeed once it reaches a provider. Providers can perform additional permission 
checking once a request arrives.

## Feature Detection

The resource API has support for feature detection. This happens through the 
[`example.retrieveProducts`](/docs/reference/example.retrieveProducts.md)  operation. Feature detection is specific to concrete resources. In 
general terms, feature detection can change:

- A provider might only support a subset of fields (of a data model).
- Some operations might be optional. A provider can declare support for advanced features.
- A provider might only support a subset of operation workloads. They can require work to follow a certain 
  structure. For example, a provider might declare support for containers but not for virtual machines. 
  
## A Note on the Examples

In the examples, we will work with a simple resource, used only in examples. This resource instructs the 
provider to count from one integer to another integer. The end-user specifies these numbers in the 
specification. The provider communicates the progress through updates. End-users can read the current 
progress from the status property.

By default, all providers support counting "forward" (in the positive direction). However, providers must 
declare that they support counting "backwards" (negative direction). If they do not declare support for 
this, then UCloud/Core will reject all requests counting backwards.

## Table of Contents
<details>
<summary>
<a href='#example-browsing-the-catalog'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-browsing-the-catalog'>Browsing the catalog</a></td></tr>
<tr><td><a href='#example-creating-and-retrieving-a-resource'>Creating and retrieving a resource</a></td></tr>
<tr><td><a href='#example-browsing-the-catalog-with-a-filter'>Browsing the catalog with a filter</a></td></tr>
<tr><td><a href='#example-searching-for-data'>Searching for data</a></td></tr>
<tr><td><a href='#example-feature-detection-(supported)'>Feature detection (Supported)</a></td></tr>
<tr><td><a href='#example-feature-detection-(failure-scenario)'>Feature detection (Failure scenario)</a></td></tr>
<tr><td><a href='#example-resource-collaboration'>Resource Collaboration</a></td></tr>
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
<td><a href='#resolvedsupport'><code>ResolvedSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourcechargecredits'><code>ResourceChargeCredits</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#sortdirection'><code>SortDirection</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#supportbyprovider'><code>SupportByProvider</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ufileupdate'><code>UFileUpdate</code></a></td>
<td>Describes an update to the `Resource`</td>
</tr>
<tr>
<td><a href='#aclentity'><code>AclEntity</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#aclentity.projectgroup'><code>AclEntity.ProjectGroup</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#aclentity.user'><code>AclEntity.User</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exampleresource'><code>ExampleResource</code></a></td>
<td>A `Resource` is the core data model used to synchronize tasks between UCloud and Provider.</td>
</tr>
<tr>
<td><a href='#exampleresource.spec'><code>ExampleResource.Spec</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exampleresource.state'><code>ExampleResource.State</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exampleresource.status'><code>ExampleResource.Status</code></a></td>
<td>Describes the current state of the `Resource`</td>
</tr>
<tr>
<td><a href='#exampleresource.update'><code>ExampleResource.Update</code></a></td>
<td>Describes an update to the `Resource`</td>
</tr>
<tr>
<td><a href='#exampleresourceflags'><code>ExampleResourceFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exampleresourcesupport'><code>ExampleResourceSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exampleresourcesupport.supported'><code>ExampleResourceSupport.Supported</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#permission'><code>Permission</code></a></td>
<td>The UCloud permission model</td>
</tr>
<tr>
<td><a href='#resourceaclentry'><code>ResourceAclEntry</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourceupdateandid'><code>ResourceUpdateAndId</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updatedaclwithresource'><code>UpdatedAclWithResource</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourcebrowserequest'><code>ResourceBrowseRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#resourceinitializationrequest'><code>ResourceInitializationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourceretrieverequest'><code>ResourceRetrieveRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#resourcesearchrequest'><code>ResourceSearchRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#resourcechargecreditsresponse'><code>ResourceChargeCreditsResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Browsing the catalog
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

/* In this example, we will discover how a user can browse their catalog. This is done through the
browse operation. The browse operation exposes the results using the pagination API of UCloud.

As we will see later, it is possible to filter in the results returned using the flags of the
operation. */

Resources.browse.call(
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
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
        sortBy = null, 
        sortDirection = SortDirection.ascending, 
    ),
    user
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

/* 📝 NOTE: The provider has already started counting. You can observe the changes which lead to the
current status through the updates. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will discover how a user can browse their catalog. This is done through the
browse operation. The browse operation exposes the results using the pagination API of UCloud.

As we will see later, it is possible to filter in the results returned using the flags of the
operation. */

// Authenticated as user
await callAPI(ExampleApi.browse(
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
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "sortBy": null,
        "sortDirection": "ascending"
    }
);

/*
{
    "itemsPerPage": 50,
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
    ],
    "next": null
}
*/

/* 📝 NOTE: The provider has already started counting. You can observe the changes which lead to the
current status through the updates. */

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

# In this example, we will discover how a user can browse their catalog. This is done through the
# browse operation. The browse operation exposes the results using the pagination API of UCloud.
# 
# As we will see later, it is possible to filter in the results returned using the flags of the
# operation.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&sortDirection=ascending" 

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

# 📝 NOTE: The provider has already started counting. You can observe the changes which lead to the
# current status through the updates.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example_browse.png)

</details>


## Example: Creating and retrieving a resource
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

/* In this example, we will discover how to create a resource and retrieve information about it. */

Resources.create.call(
    bulkRequestOf(ExampleResource.Spec(
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        start = 0, 
        target = 100, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "1234", 
    )), 
)
*/

/* 📝 NOTE: Users only specify the specification when they wish to create a resource. The specification
defines the values which are in the control of the user. The specification remains immutable
for the resource's lifetime. Mutable values are instead listed in the status. */

Resources.retrieve.call(
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
    user
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will discover how to create a resource and retrieve information about it. */

// Authenticated as user
await callAPI(ExampleApi.create(
    {
        "items": [
            {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
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
            "id": "1234"
        }
    ]
}
*/

/* 📝 NOTE: Users only specify the specification when they wish to create a resource. The specification
defines the values which are in the control of the user. The specification remains immutable
for the resource's lifetime. Mutable values are instead listed in the status. */

await callAPI(ExampleApi.retrieve(
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
        "id": "1234"
    }
);

/*
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

# In this example, we will discover how to create a resource and retrieve information about it.

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example" -d '{
    "items": [
        {
            "start": 0,
            "target": 100,
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "1234"
#         }
#     ]
# }

# 📝 NOTE: Users only specify the specification when they wish to create a resource. The specification
# defines the values which are in the control of the user. The specification remains immutable
# for the resource's lifetime. Mutable values are instead listed in the status.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=1234" 

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

![](/docs/diagrams/example_create.png)

</details>


## Example: Browsing the catalog with a filter
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

/* In this example, we will look at the flags which are passed to both browse and retrieve operations.
This value is used to:

- Filter out values: These properties are prefixed by filter* and remove results from the response.
  When used in a retrieve operation, this will cause a 404 if no results are found.
- Include additional data: These properties are prefixed by include* and can be used to load 
  additional data. This data is returned as part of the status object. The intention of these are to
  save the client a round-trip by retrieving all relevant data in a single call. */

Resources.browse.call(
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
            filterProviderIds = null, 
            filterState = State.RUNNING, 
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
    user
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will look at the flags which are passed to both browse and retrieve operations.
This value is used to:

- Filter out values: These properties are prefixed by filter* and remove results from the response.
  When used in a retrieve operation, this will cause a 404 if no results are found.
- Include additional data: These properties are prefixed by include* and can be used to load 
  additional data. This data is returned as part of the status object. The intention of these are to
  save the client a round-trip by retrieving all relevant data in a single call. */

// Authenticated as user
await callAPI(ExampleApi.browse(
    {
        "flags": {
            "filterState": "RUNNING",
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
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "sortBy": null,
        "sortDirection": "ascending"
    }
);

/*
{
    "itemsPerPage": 50,
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

# In this example, we will look at the flags which are passed to both browse and retrieve operations.
# This value is used to:
# 
# - Filter out values: These properties are prefixed by filter* and remove results from the response.
#   When used in a retrieve operation, this will cause a 404 if no results are found.
# - Include additional data: These properties are prefixed by include* and can be used to load 
#   additional data. This data is returned as part of the status object. The intention of these are to
#   save the client a round-trip by retrieving all relevant data in a single call.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/browse?filterState=RUNNING&includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&sortDirection=ascending" 

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

![](/docs/diagrams/example_filtering.png)

</details>


## Example: Searching for data
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

/* In this example, we will discover the search functionality of resources. Search allows for free-text
queries which attempts to find relevant results. This is very different from browse with filters, 
since 'relevancy' is a vaguely defined concept. Search is not guaranteed to return results in any
deterministic fashion, unlike browse. */


/* We start with the following dataset. */

Resources.browse.call(
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
            filterProviderIds = null, 
            filterState = State.RUNNING, 
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
    user
).orThrow()

/*
PageV2(
    items = listOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "1", 
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
        updates = emptyList(), 
        providerGeneratedId = "1", 
    ), ExampleResource(
        createdAt = 1635170395571, 
        id = "2", 
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
            target = 200, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "2", 
    ), ExampleResource(
        createdAt = 1635170395571, 
        id = "3", 
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
            target = 300, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "3", 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* Search may look in many different fields to determine if a result is relevant. Searching for the
value 300 might produce the following results. */

Resources.search.call(
    ResourceSearchRequest(
        consistency = null, 
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
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
        query = "300", 
        sortBy = null, 
        sortDirection = SortDirection.ascending, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(ExampleResource(
        createdAt = 1635170395571, 
        id = "3", 
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
            target = 300, 
        ), 
        status = ExampleResource.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            state = State.RUNNING, 
            value = 10, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "3", 
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

/* In this example, we will discover the search functionality of resources. Search allows for free-text
queries which attempts to find relevant results. This is very different from browse with filters, 
since 'relevancy' is a vaguely defined concept. Search is not guaranteed to return results in any
deterministic fashion, unlike browse. */


/* We start with the following dataset. */

// Authenticated as user
await callAPI(ExampleApi.browse(
    {
        "flags": {
            "filterState": "RUNNING",
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
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "sortBy": null,
        "sortDirection": "ascending"
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "id": "1",
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
            "id": "2",
            "specification": {
                "start": 0,
                "target": 200,
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
            "id": "3",
            "specification": {
                "start": 0,
                "target": 300,
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
    ],
    "next": null
}
*/

/* Search may look in many different fields to determine if a result is relevant. Searching for the
value 300 might produce the following results. */

await callAPI(ExampleApi.search(
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
        "query": "300",
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "sortBy": null,
        "sortDirection": "ascending"
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "id": "3",
            "specification": {
                "start": 0,
                "target": 300,
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

# In this example, we will discover the search functionality of resources. Search allows for free-text
# queries which attempts to find relevant results. This is very different from browse with filters, 
# since 'relevancy' is a vaguely defined concept. Search is not guaranteed to return results in any
# deterministic fashion, unlike browse.

# We start with the following dataset.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/browse?filterState=RUNNING&includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "1",
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
#         },
#         {
#             "id": "2",
#             "specification": {
#                 "start": 0,
#                 "target": 200,
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
#         },
#         {
#             "id": "3",
#             "specification": {
#                 "start": 0,
#                 "target": 300,
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

# Search may look in many different fields to determine if a result is relevant. Searching for the
# value 300 might produce the following results.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example/search" -d '{
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
    "query": "300",
    "itemsPerPage": null,
    "next": null,
    "consistency": null,
    "itemsToSkip": null,
    "sortBy": null,
    "sortDirection": "ascending"
}'


# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "3",
#             "specification": {
#                 "start": 0,
#                 "target": 300,
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

![](/docs/diagrams/example_search.png)

</details>


## Example: Feature detection (Supported)
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

/* In this example, we will show how to use the feature detection feature of resources. Recall, that
providers need to specify if they support counting backwards. */

Resources.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.Compute(
            category = ProductCategoryId(
                id = "example-compute", 
                name = "example-compute", 
                provider = "example", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            cpu = 1, 
            description = "An example machine", 
            freeToUse = false, 
            gpu = null, 
            hiddenInGrantApplications = false, 
            memoryInGigs = 1, 
            name = "example-compute", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.COMPUTE, 
            unitOfPrice = ProductPriceUnit.UNITS_PER_HOUR, 
            version = 1, 
            balance = null, 
            id = "example-compute", 
        ), 
        support = ExampleResourceSupport(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            supportsBackwardsCounting = Supported.SUPPORTED, 
        ), 
    ))), 
)
*/

/* In this case, the provider supports counting backwards. */


/* Creating a resource which counts backwards should succeed. */

Resources.create.call(
    bulkRequestOf(ExampleResource.Spec(
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        start = 0, 
        target = -100, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "1234", 
    )), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how to use the feature detection feature of resources. Recall, that
providers need to specify if they support counting backwards. */

// Authenticated as user
await callAPI(ExampleApi.retrieveProducts(
    {
    }
);

/*
{
    "productsByProvider": {
        "example": [
            {
                "product": {
                    "type": "compute",
                    "balance": null,
                    "name": "example-compute",
                    "pricePerUnit": 1,
                    "category": {
                        "name": "example-compute",
                        "provider": "example"
                    },
                    "description": "An example machine",
                    "priority": 0,
                    "cpu": 1,
                    "memoryInGigs": 1,
                    "gpu": null,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "UNITS_PER_HOUR",
                    "chargeType": "ABSOLUTE",
                    "hiddenInGrantApplications": false,
                    "productType": "COMPUTE"
                },
                "support": {
                    "product": {
                        "id": "example-compute",
                        "category": "example-compute",
                        "provider": "example"
                    },
                    "supportsBackwardsCounting": "SUPPORTED"
                }
            }
        ]
    }
}
*/

/* In this case, the provider supports counting backwards. */


/* Creating a resource which counts backwards should succeed. */

await callAPI(ExampleApi.create(
    {
        "items": [
            {
                "start": 0,
                "target": -100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
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
            "id": "1234"
        }
    ]
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

# In this example, we will show how to use the feature detection feature of resources. Recall, that
# providers need to specify if they support counting backwards.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "type": "compute",
#                     "balance": null,
#                     "name": "example-compute",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-compute",
#                         "provider": "example"
#                     },
#                     "description": "An example machine",
#                     "priority": 0,
#                     "cpu": 1,
#                     "memoryInGigs": 1,
#                     "gpu": null,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "UNITS_PER_HOUR",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "COMPUTE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "example-compute",
#                         "category": "example-compute",
#                         "provider": "example"
#                     },
#                     "supportsBackwardsCounting": "SUPPORTED"
#                 }
#             }
#         ]
#     }
# }

# In this case, the provider supports counting backwards.

# Creating a resource which counts backwards should succeed.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example" -d '{
    "items": [
        {
            "start": 0,
            "target": -100,
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "1234"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example_feature_detection.png)

</details>


## Example: Feature detection (Failure scenario)
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

/* In this example, we will show how to use the feature detection feature of resources. Recall, that
providers need to specify if they support counting backwards. */

Resources.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.Compute(
            category = ProductCategoryId(
                id = "example-compute", 
                name = "example-compute", 
                provider = "example", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            cpu = 1, 
            description = "An example machine", 
            freeToUse = false, 
            gpu = null, 
            hiddenInGrantApplications = false, 
            memoryInGigs = 1, 
            name = "example-compute", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.COMPUTE, 
            unitOfPrice = ProductPriceUnit.UNITS_PER_HOUR, 
            version = 1, 
            balance = null, 
            id = "example-compute", 
        ), 
        support = ExampleResourceSupport(
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute", 
                provider = "example", 
            ), 
            supportsBackwardsCounting = Supported.NOT_SUPPORTED, 
        ), 
    ))), 
)
*/

/* In this case, the provider does not support counting backwards. */


/* Creating a resource which counts backwards should fail. */

Resources.create.call(
    bulkRequestOf(ExampleResource.Spec(
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        start = 0, 
        target = -100, 
    )),
    user
).orThrow()

/*
HttpStatusCode(value=400, description=Bad Request)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how to use the feature detection feature of resources. Recall, that
providers need to specify if they support counting backwards. */

// Authenticated as user
await callAPI(ExampleApi.retrieveProducts(
    {
    }
);

/*
{
    "productsByProvider": {
        "example": [
            {
                "product": {
                    "type": "compute",
                    "balance": null,
                    "name": "example-compute",
                    "pricePerUnit": 1,
                    "category": {
                        "name": "example-compute",
                        "provider": "example"
                    },
                    "description": "An example machine",
                    "priority": 0,
                    "cpu": 1,
                    "memoryInGigs": 1,
                    "gpu": null,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "UNITS_PER_HOUR",
                    "chargeType": "ABSOLUTE",
                    "hiddenInGrantApplications": false,
                    "productType": "COMPUTE"
                },
                "support": {
                    "product": {
                        "id": "example-compute",
                        "category": "example-compute",
                        "provider": "example"
                    },
                    "supportsBackwardsCounting": "NOT_SUPPORTED"
                }
            }
        ]
    }
}
*/

/* In this case, the provider does not support counting backwards. */


/* Creating a resource which counts backwards should fail. */

await callAPI(ExampleApi.create(
    {
        "items": [
            {
                "start": 0,
                "target": -100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                }
            }
        ]
    }
);

/*
HttpStatusCode(value=400, description=Bad Request)
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

# In this example, we will show how to use the feature detection feature of resources. Recall, that
# providers need to specify if they support counting backwards.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "type": "compute",
#                     "balance": null,
#                     "name": "example-compute",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-compute",
#                         "provider": "example"
#                     },
#                     "description": "An example machine",
#                     "priority": 0,
#                     "cpu": 1,
#                     "memoryInGigs": 1,
#                     "gpu": null,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "UNITS_PER_HOUR",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "COMPUTE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "example-compute",
#                         "category": "example-compute",
#                         "provider": "example"
#                     },
#                     "supportsBackwardsCounting": "NOT_SUPPORTED"
#                 }
#             }
#         ]
#     }
# }

# In this case, the provider does not support counting backwards.

# Creating a resource which counts backwards should fail.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example" -d '{
    "items": [
        {
            "start": 0,
            "target": -100,
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            }
        }
    ]
}'


# HttpStatusCode(value=400, description=Bad Request)

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/example_feature_detection_failure.png)

</details>


## Example: Resource Collaboration
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr>
<th>Actors</th>
<td><ul>
<li>A UCloud user named Alice (<code>alice</code>)</li>
<li>A UCloud user named Bob (<code>bob</code>)</li>
</ul></td>
</tr>
</table>
<details>
<summary>
<b>Communication Flow:</b> Kotlin
</summary>

```kotlin

/* In this example, we discover how to use the resource collaboration features of UCloud. This example
involves two users: Alice and Bob. */


/* Alice starts by creating a resource */

Resources.create.call(
    bulkRequestOf(ExampleResource.Spec(
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        start = 0, 
        target = 100, 
    )),
    alice
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "1234", 
    )), 
)
*/

/* By default, Bob doesn't have access to this resource. Attempting to retrieve it will fail. */

Resources.retrieve.call(
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
    bob
).orThrow()

/*
HttpStatusCode(value=404, description=Not Found)
*/

/* Alice can change the permissions of the resource by invoking updateAcl. This causes Bob to gain READ permissions. */

Resources.updateAcl.call(
    bulkRequestOf(UpdatedAcl(
        added = listOf(ResourceAclEntry(
            entity = AclEntity.ProjectGroup(
                group = "Group of Bob", 
                projectId = "Project", 
            ), 
            permissions = listOf(Permission.READ), 
        )), 
        deleted = emptyList(), 
        id = "1234", 
    )),
    alice
).orThrow()

/*
BulkResponse(
    responses = listOf(Unit), 
)
*/

/* Bob can now retrieve the resource. */

Resources.retrieve.call(
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
    bob
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
        myself = listOf(Permission.READ), 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we discover how to use the resource collaboration features of UCloud. This example
involves two users: Alice and Bob. */


/* Alice starts by creating a resource */

// Authenticated as alice
await callAPI(ExampleApi.create(
    {
        "items": [
            {
                "start": 0,
                "target": 100,
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
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
            "id": "1234"
        }
    ]
}
*/

/* By default, Bob doesn't have access to this resource. Attempting to retrieve it will fail. */

// Authenticated as bob
await callAPI(ExampleApi.retrieve(
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
        "id": "1234"
    }
);

/*
HttpStatusCode(value=404, description=Not Found)
*/

/* Alice can change the permissions of the resource by invoking updateAcl. This causes Bob to gain READ permissions. */

// Authenticated as alice
await callAPI(ExampleApi.updateAcl(
    {
        "items": [
            {
                "id": "1234",
                "added": [
                    {
                        "entity": {
                            "type": "project_group",
                            "projectId": "Project",
                            "group": "Group of Bob"
                        },
                        "permissions": [
                            "READ"
                        ]
                    }
                ],
                "deleted": [
                ]
            }
        ]
    }
);

/*
{
    "responses": [
        {
        }
    ]
}
*/

/* Bob can now retrieve the resource. */

// Authenticated as bob
await callAPI(ExampleApi.retrieve(
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
        "id": "1234"
    }
);

/*
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
            "READ"
        ],
        "others": [
        ]
    }
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

# In this example, we discover how to use the resource collaboration features of UCloud. This example
# involves two users: Alice and Bob.

# Alice starts by creating a resource

# Authenticated as alice
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example" -d '{
    "items": [
        {
            "start": 0,
            "target": 100,
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "1234"
#         }
#     ]
# }

# By default, Bob doesn't have access to this resource. Attempting to retrieve it will fail.

# Authenticated as bob
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=1234" 

# HttpStatusCode(value=404, description=Not Found)

# Alice can change the permissions of the resource by invoking updateAcl. This causes Bob to gain READ permissions.

# Authenticated as alice
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/example/updateAcl" -d '{
    "items": [
        {
            "id": "1234",
            "added": [
                {
                    "entity": {
                        "type": "project_group",
                        "projectId": "Project",
                        "group": "Group of Bob"
                    },
                    "permissions": [
                        "READ"
                    ]
                }
            ],
            "deleted": [
            ]
        }
    ]
}'


# {
#     "responses": [
#         {
#         }
#     ]
# }

# Bob can now retrieve the resource.

# Authenticated as bob
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=1234" 

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
#             "READ"
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

![](/docs/diagrams/example_collaboration.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#resourcebrowserequest'>ResourceBrowseRequest</a>&lt;<a href='#exampleresourceflags'>ExampleResourceFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#exampleresource'>ExampleResource</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#resourceretrieverequest'>ResourceRetrieveRequest</a>&lt;<a href='#exampleresourceflags'>ExampleResourceFlags</a>&gt;</code>|<code><a href='#exampleresource'>ExampleResource</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#supportbyprovider'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#exampleresourcesupport'>ExampleResourceSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `search`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#resourcesearchrequest'>ResourceSearchRequest</a>&lt;<a href='#exampleresourceflags'>ExampleResourceFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#exampleresource'>ExampleResource</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#exampleresource.spec'>ExampleResource.Spec</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `init`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
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

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `ResolvedSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResolvedSupport<P, Support>(
    val product: P,
    val support: Support,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code>P</code></code>
</summary>





</details>

<details>
<summary>
<code>support</code>: <code><code>Support</code></code>
</summary>





</details>



</details>



---

### `ResourceChargeCredits`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceChargeCredits(
    val id: String,
    val chargeId: String,
    val units: Long,
    val periods: Long?,
    val performedBy: String?,
    val description: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the `Resource`
</summary>





</details>

<details>
<summary>
<code>chargeId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the charge
</summary>



This charge ID must be unique for the `Resource`, UCloud will reject charges which are not unique.


</details>

<details>
<summary>
<code>units</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Amount of units to charge the user
</summary>





</details>

<details>
<summary>
<code>periods</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>performedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `SortDirection`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class SortDirection {
    ascending,
    descending,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ascending</code>
</summary>





</details>

<details>
<summary>
<code>descending</code>
</summary>





</details>



</details>



---

### `SupportByProvider`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SupportByProvider<P, S>(
    val productsByProvider: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>productsByProvider</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

### `UFileUpdate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes an update to the `Resource`_

```kotlin
data class UFileUpdate(
    val timestamp: Long,
    val status: String?,
)
```
Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> A timestamp referencing when UCloud received this update
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A generic text message describing the current status of the `Resource`
</summary>





</details>



</details>



---

### `AclEntity`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class AclEntity {
    class ProjectGroup : AclEntity()
    class User : AclEntity()
}
```



---

### `AclEntity.ProjectGroup`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ProjectGroup(
    val projectId: String,
    val group: String,
    val type: String /* "project_group" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>projectId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>group</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "project_group" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AclEntity.User`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class User(
    val username: String,
    val type: String /* "user" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>username</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "user" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ExampleResource`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Resource` is the core data model used to synchronize tasks between UCloud and Provider._

```kotlin
data class ExampleResource(
    val id: String,
    val specification: ExampleResource.Spec,
    val createdAt: Long,
    val status: ExampleResource.Status,
    val updates: List<ExampleResource.Update>,
    val owner: ResourceOwner,
    val permissions: ResourcePermissions?,
    val providerGeneratedId: String?,
)
```
For more information go [here](/docs/developer-guide/orchestration/resources.md).

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
<code>specification</code>: <code><code><a href='#exampleresource.spec'>ExampleResource.Spec</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#exampleresource.status'>ExampleResource.Status</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#exampleresource.update'>ExampleResource.Update</a>&gt;</code></code> Contains a list of updates from the provider as well as UCloud
</summary>



Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> Contains information about the original creator of the `Resource` along with project association
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





</details>



</details>



---

### `ExampleResource.Spec`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Spec(
    val start: Int,
    val target: Int,
    val product: ProductReference,
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
<code>target</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which backs this `Resource`
</summary>





</details>



</details>



---

### `ExampleResource.State`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class State {
    PENDING,
    RUNNING,
    DONE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PENDING</code>
</summary>





</details>

<details>
<summary>
<code>RUNNING</code>
</summary>





</details>

<details>
<summary>
<code>DONE</code>
</summary>





</details>



</details>



---

### `ExampleResource.Status`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class Status(
    val state: ExampleResource.State,
    val value: Int,
    val resolvedSupport: ResolvedSupport<Product, ExampleResourceSupport>?,
    val resolvedProduct: Product?,
)
```
The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#exampleresource.state'>ExampleResource.State</a></code></code>
</summary>





</details>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='#resolvedsupport'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#exampleresourcesupport'>ExampleResourceSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `ExampleResource.Update`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes an update to the `Resource`_

```kotlin
data class Update(
    val timestamp: Long?,
    val status: String?,
    val newState: ExampleResource.State?,
    val currentValue: Int?,
)
```
Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp referencing when UCloud received this update
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A generic text message describing the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>newState</code>: <code><code><a href='#exampleresource.state'>ExampleResource.State</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>currentValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `ExampleResourceFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExampleResourceFlags(
    val filterState: ExampleResource.State?,
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
<code>filterState</code>: <code><code><a href='#exampleresource.state'>ExampleResource.State</a>?</code></code>
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

### `ExampleResourceSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExampleResourceSupport(
    val product: ProductReference,
    val supportsBackwardsCounting: ExampleResourceSupport.Supported?,
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
<code>supportsBackwardsCounting</code>: <code><code><a href='#exampleresourcesupport.supported'>ExampleResourceSupport.Supported</a>?</code></code>
</summary>





</details>



</details>



---

### `ExampleResourceSupport.Supported`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Supported {
    SUPPORTED,
    NOT_SUPPORTED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>SUPPORTED</code>
</summary>





</details>

<details>
<summary>
<code>NOT_SUPPORTED</code>
</summary>





</details>



</details>



---

### `Permission`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The UCloud permission model_

```kotlin
enum class Permission {
    READ,
    EDIT,
    ADMIN,
    PROVIDER,
}
```
This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of
standard permissions that can be applied to a resource and its associated operations.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>READ</code> Grants an entity access to all read-based operations
</summary>



Read-based operations must not alter the state of a resource. Typical examples include the `browse` and
`retrieve*` endpoints.


</details>

<details>
<summary>
<code>EDIT</code> Grants an entity access to all write-based operations
</summary>



Write-based operations are allowed to alter the state of a resource. This permission is required for most
`update*` endpoints.


</details>

<details>
<summary>
<code>ADMIN</code> Grants an entity access to special privileged operations
</summary>



This permission will allow the entity to perform any action on the resource, unless the operation
specifies otherwise. This operation is, for example, used for updating the permissions attached to a
resource.


</details>

<details>
<summary>
<code>PROVIDER</code> Grants an entity access to special privileged operations specific to a provider
</summary>





</details>



</details>



---

### `ResourceAclEntry`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceAclEntry(
    val entity: AclEntity,
    val permissions: List<Permission>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>entity</code>: <code><code><a href='#aclentity'>AclEntity</a></code></code>
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#permission'>Permission</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ResourceUpdateAndId`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceUpdateAndId<U>(
    val id: String,
    val update: U,
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
<code>update</code>: <code><code>U</code></code>
</summary>





</details>



</details>



---

### `UpdatedAclWithResource`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdatedAclWithResource<Res>(
    val resource: Res,
    val added: List<ResourceAclEntry>,
    val deleted: List<AclEntity>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>resource</code>: <code><code>Res</code></code>
</summary>





</details>

<details>
<summary>
<code>added</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#resourceaclentry'>ResourceAclEntry</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>deleted</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#aclentity'>AclEntity</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ResourceBrowseRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class ResourceBrowseRequest<Flags>(
    val flags: Flags,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val sortBy: String?,
    val sortDirection: SortDirection?,
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
<code>flags</code>: <code><code>Flags</code></code>
</summary>





</details>

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
<code>sortBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>sortDirection</code>: <code><code><a href='#sortdirection'>SortDirection</a>?</code></code>
</summary>





</details>



</details>



---

### `ResourceInitializationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceInitializationRequest(
    val principal: ResourceOwner,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>principal</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code>
</summary>





</details>



</details>



---

### `ResourceRetrieveRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceRetrieveRequest<Flags>(
    val flags: Flags,
    val id: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>flags</code>: <code><code>Flags</code></code>
</summary>





</details>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ResourceSearchRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class ResourceSearchRequest<Flags>(
    val flags: Flags,
    val query: String,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
    val sortBy: String?,
    val sortDirection: SortDirection?,
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
<code>flags</code>: <code><code>Flags</code></code>
</summary>





</details>

<details>
<summary>
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

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
<code>sortBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>sortDirection</code>: <code><code><a href='#sortdirection'>SortDirection</a>?</code></code>
</summary>





</details>



</details>



---

### `ResourceChargeCreditsResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ResourceChargeCreditsResponse(
    val insufficientFunds: List<FindByStringId>,
    val duplicateCharges: List<FindByStringId>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>insufficientFunds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code></code> A list of resources which could not be charged due to lack of funds. If all resources were charged successfully then this will empty.
</summary>





</details>

<details>
<summary>
<code>duplicateCharges</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code></code> A list of resources which could not be charged due to it being a duplicate charge. If all resources were charged successfully this will be empty.
</summary>





</details>



</details>



---

