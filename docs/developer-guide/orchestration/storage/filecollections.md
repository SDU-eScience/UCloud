<p align='center'>
<a href='/docs/developer-guide/orchestration/resources.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/files.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / Drives (FileCollection)
# Drives (FileCollection)

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_A FileCollection is an entrypoint to a user's files_

## Rationale

__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    

This entrypoint allows the user to access all the files they have access to within a single project. It is important to
note that a file collection is not the same as a directory! Common real-world examples of a file collection is listed
below:

| Name              | Typical path                | Comment                                                     |
|-------------------|-----------------------------|-------------------------------------------------------------|
| Home directory    | `/home/$username/`     | The home folder is typically the main collection for a user |
| Work directory    | `/work/$projectId/`    | The project 'home' folder                                   |
| Scratch directory | `/scratch/$projectId/` | Temporary storage for a project                             |

The provider of storage manages a 'database' of these file collections and who they belong to. The file collections also
play an important role in accounting and resource management. A file collection can have a quota attached to it and
billing information is also stored in this object. Each file collection can be attached to a different product type, and
as a result, can have different billing information attached to it. This is, for example, useful if a storage provider
has both fast and slow tiers of storage, which is typically billed very differently.

All file collections additionally have a title. This title can be used for a user-friendly version of the folder. This
title does not have to be unique, and can with great benefit choose to not reference who it belongs to. For example,
if every user has exactly one home directory, then it would make sense to give this collection `"Home"` as its title.

---

__📝 Provider Note:__ This is the API exposed to end-users. See the table below for other relevant APIs.

| End-User | Provider (Ingoing) | Control (Outgoing) |
|----------|--------------------|--------------------|
| [`FileCollections`](/docs/developer-guide/orchestration/storage/filecollections.md) | [`FileCollectionsProvider`](/docs/developer-guide/orchestration/storage/providers/drives/ingoing.md) | [`FileCollectionsControl`](/docs/developer-guide/orchestration/storage/providers/drives/outgoing.md) |

---

## Table of Contents
<details>
<summary>
<a href='#example-an-example-collection'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-an-example-collection'>An example collection</a></td></tr>
<tr><td><a href='#example-renaming-a-collection'>Renaming a collection</a></td></tr>
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
<td><a href='#rename'><code>rename</code></a></td>
<td><i>No description</i></td>
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
<td><a href='#fscollectionsupport'><code>FSCollectionSupport</code></a></td>
<td>Declares which `FileCollection` operations are supported for a product</td>
</tr>
<tr>
<td><a href='#fsfilesupport'><code>FSFileSupport</code></a></td>
<td>Declares which file-level operations a product supports</td>
</tr>
<tr>
<td><a href='#fsproductstatssupport'><code>FSProductStatsSupport</code></a></td>
<td>Declares which stats a given product supports</td>
</tr>
<tr>
<td><a href='#fssupport'><code>FSSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filecollection'><code>FileCollection</code></a></td>
<td></td>
</tr>
<tr>
<td><a href='#filecollection.spec'><code>FileCollection.Spec</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filecollection.status'><code>FileCollection.Status</code></a></td>
<td>Describes the current state of the `Resource`</td>
</tr>
<tr>
<td><a href='#filecollection.update'><code>FileCollection.Update</code></a></td>
<td>Describes an update to the `Resource`</td>
</tr>
<tr>
<td><a href='#filecollectionincludeflags'><code>FileCollectionIncludeFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#memberfilesfilter'><code>MemberFilesFilter</code></a></td>
<td>Filter for member files.</td>
</tr>
<tr>
<td><a href='#filecollectionsrenamerequestitem'><code>FileCollectionsRenameRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: An example collection
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

/* In this example we will see a simple collection. This collection models the 'home' directory of a user. */


/* 📝 NOTE: Collections are identified by a unique (UCloud provided) ID */

FileCollections.retrieve.call(
    ResourceRetrieveRequest(
        flags = FileCollectionIncludeFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterMemberFiles = null, 
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
        id = "54123", 
    ),
    user
).orThrow()

/*
FileCollection(
    createdAt = 1635151675465, 
    id = "54123", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = ResourcePermissions(
        myself = listOf(Permission.ADMIN), 
        others = emptyList(), 
    ), 
    providerGeneratedId = null, 
    specification = FileCollection.Spec(
        product = ProductReference(
            category = "example-ssd", 
            id = "example-ssd", 
            provider = "example", 
        ), 
        title = "Home", 
    ), 
    status = FileCollection.Status(
        resolvedProduct = null, 
        resolvedSupport = null, 
    ), 
    updates = emptyList(), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example we will see a simple collection. This collection models the 'home' directory of a user. */


/* 📝 NOTE: Collections are identified by a unique (UCloud provided) ID */

// Authenticated as user
await callAPI(FilesCollectionsApi.retrieve(
    {
        "flags": {
            "filterMemberFiles": null,
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
        "id": "54123"
    }
);

/*
{
    "id": "54123",
    "specification": {
        "title": "Home",
        "product": {
            "id": "example-ssd",
            "category": "example-ssd",
            "provider": "example"
        }
    },
    "createdAt": 1635151675465,
    "status": {
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
    },
    "providerGeneratedId": null
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

# In this example we will see a simple collection. This collection models the 'home' directory of a user.

# 📝 NOTE: Collections are identified by a unique (UCloud provided) ID

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/collections/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=54123" 

# {
#     "id": "54123",
#     "specification": {
#         "title": "Home",
#         "product": {
#             "id": "example-ssd",
#             "category": "example-ssd",
#             "provider": "example"
#         }
#     },
#     "createdAt": 1635151675465,
#     "status": {
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "updates": [
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
#     },
#     "providerGeneratedId": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files.collections_retrieve.png)

</details>


## Example: Renaming a collection
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

/* In this example, we will see how a user can rename one of their collections. */


/* 📝 NOTE: Renaming must be supported by the provider */

FileCollections.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("ucloud" to listOf(ResolvedSupport(
        product = Product.Storage(
            category = ProductCategoryId(
                id = "example-ssd", 
                name = "example-ssd", 
                provider = "example", 
            ), 
            chargeType = ChargeType.DIFFERENTIAL_QUOTA, 
            description = "Fast storage", 
            freeToUse = false, 
            hiddenInGrantApplications = false, 
            name = "example-ssd", 
            pricePerUnit = 1, 
            priority = 0, 
            productType = ProductType.STORAGE, 
            unitOfPrice = ProductPriceUnit.PER_UNIT, 
            version = 1, 
            balance = null, 
            id = "example-ssd", 
        ), 
        support = FSSupport(
            collection = FSCollectionSupport(
                aclModifiable = null, 
                usersCanCreate = true, 
                usersCanDelete = true, 
                usersCanRename = true, 
            ), 
            files = FSFileSupport(
                aclModifiable = false, 
                isReadOnly = false, 
                searchSupported = true, 
                streamingSearchSupported = false, 
                trashSupported = false, 
            ), 
            product = ProductReference(
                category = "example-ssd", 
                id = "example-ssd", 
                provider = "example", 
            ), 
            stats = FSProductStatsSupport(
                accessedAt = null, 
                createdAt = null, 
                modifiedAt = null, 
                sizeInBytes = null, 
                sizeIncludingChildrenInBytes = null, 
                unixGroup = null, 
                unixOwner = null, 
                unixPermissions = null, 
            ), 
        ), 
    ))), 
)
*/

/* As we can see, the provider does support the rename operation. We now look at our collections. */

FileCollections.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = FileCollectionIncludeFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterMemberFiles = null, 
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
    items = listOf(FileCollection(
        createdAt = 1635151675465, 
        id = "54123", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        providerGeneratedId = null, 
        specification = FileCollection.Spec(
            product = ProductReference(
                category = "example-ssd", 
                id = "example-ssd", 
                provider = "example", 
            ), 
            title = "Home", 
        ), 
        status = FileCollection.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
        ), 
        updates = emptyList(), 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* Using the unique ID, we can rename the collection */

FileCollections.rename.call(
    bulkRequestOf(FileCollectionsRenameRequestItem(
        id = "54123", 
        newTitle = "My Awesome Drive", 
    )),
    user
).orThrow()

/*
Unit
*/

/* The new title is observed when we browse the collections one more time */

FileCollections.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = FileCollectionIncludeFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterMemberFiles = null, 
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
    items = listOf(FileCollection(
        createdAt = 1635151675465, 
        id = "54123", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        providerGeneratedId = null, 
        specification = FileCollection.Spec(
            product = ProductReference(
                category = "example-ssd", 
                id = "example-ssd", 
                provider = "example", 
            ), 
            title = "My Awesome Drive", 
        ), 
        status = FileCollection.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
        ), 
        updates = emptyList(), 
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

/* In this example, we will see how a user can rename one of their collections. */


/* 📝 NOTE: Renaming must be supported by the provider */

// Authenticated as user
await callAPI(FilesCollectionsApi.retrieveProducts(
    {
    }
);

/*
{
    "productsByProvider": {
        "ucloud": [
            {
                "product": {
                    "balance": null,
                    "name": "example-ssd",
                    "pricePerUnit": 1,
                    "category": {
                        "name": "example-ssd",
                        "provider": "example"
                    },
                    "description": "Fast storage",
                    "priority": 0,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "PER_UNIT",
                    "chargeType": "DIFFERENTIAL_QUOTA",
                    "hiddenInGrantApplications": false,
                    "productType": "STORAGE"
                },
                "support": {
                    "product": {
                        "id": "example-ssd",
                        "category": "example-ssd",
                        "provider": "example"
                    },
                    "stats": {
                        "sizeInBytes": null,
                        "sizeIncludingChildrenInBytes": null,
                        "modifiedAt": null,
                        "createdAt": null,
                        "accessedAt": null,
                        "unixPermissions": null,
                        "unixOwner": null,
                        "unixGroup": null
                    },
                    "collection": {
                        "aclModifiable": null,
                        "usersCanCreate": true,
                        "usersCanDelete": true,
                        "usersCanRename": true
                    },
                    "files": {
                        "aclModifiable": false,
                        "trashSupported": false,
                        "isReadOnly": false,
                        "searchSupported": true,
                        "streamingSearchSupported": false
                    }
                }
            }
        ]
    }
}
*/

/* As we can see, the provider does support the rename operation. We now look at our collections. */

await callAPI(FilesCollectionsApi.browse(
    {
        "flags": {
            "filterMemberFiles": null,
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
            "id": "54123",
            "specification": {
                "title": "Home",
                "product": {
                    "id": "example-ssd",
                    "category": "example-ssd",
                    "provider": "example"
                }
            },
            "createdAt": 1635151675465,
            "status": {
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
            },
            "providerGeneratedId": null
        }
    ],
    "next": null
}
*/

/* Using the unique ID, we can rename the collection */

await callAPI(FilesCollectionsApi.rename(
    {
        "items": [
            {
                "id": "54123",
                "newTitle": "My Awesome Drive"
            }
        ]
    }
);

/*
{
}
*/

/* The new title is observed when we browse the collections one more time */

await callAPI(FilesCollectionsApi.browse(
    {
        "flags": {
            "filterMemberFiles": null,
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
            "id": "54123",
            "specification": {
                "title": "My Awesome Drive",
                "product": {
                    "id": "example-ssd",
                    "category": "example-ssd",
                    "provider": "example"
                }
            },
            "createdAt": 1635151675465,
            "status": {
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
            },
            "providerGeneratedId": null
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

# In this example, we will see how a user can rename one of their collections.

# 📝 NOTE: Renaming must be supported by the provider

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/collections/retrieveProducts" 

# {
#     "productsByProvider": {
#         "ucloud": [
#             {
#                 "product": {
#                     "balance": null,
#                     "name": "example-ssd",
#                     "pricePerUnit": 1,
#                     "category": {
#                         "name": "example-ssd",
#                         "provider": "example"
#                     },
#                     "description": "Fast storage",
#                     "priority": 0,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "PER_UNIT",
#                     "chargeType": "DIFFERENTIAL_QUOTA",
#                     "hiddenInGrantApplications": false,
#                     "productType": "STORAGE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "example-ssd",
#                         "category": "example-ssd",
#                         "provider": "example"
#                     },
#                     "stats": {
#                         "sizeInBytes": null,
#                         "sizeIncludingChildrenInBytes": null,
#                         "modifiedAt": null,
#                         "createdAt": null,
#                         "accessedAt": null,
#                         "unixPermissions": null,
#                         "unixOwner": null,
#                         "unixGroup": null
#                     },
#                     "collection": {
#                         "aclModifiable": null,
#                         "usersCanCreate": true,
#                         "usersCanDelete": true,
#                         "usersCanRename": true
#                     },
#                     "files": {
#                         "aclModifiable": false,
#                         "trashSupported": false,
#                         "isReadOnly": false,
#                         "searchSupported": true,
#                         "streamingSearchSupported": false
#                     }
#                 }
#             }
#         ]
#     }
# }

# As we can see, the provider does support the rename operation. We now look at our collections.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/collections/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "54123",
#             "specification": {
#                 "title": "Home",
#                 "product": {
#                     "id": "example-ssd",
#                     "category": "example-ssd",
#                     "provider": "example"
#                 }
#             },
#             "createdAt": 1635151675465,
#             "status": {
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
#             },
#             "providerGeneratedId": null
#         }
#     ],
#     "next": null
# }

# Using the unique ID, we can rename the collection

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/collections/rename" -d '{
    "items": [
        {
            "id": "54123",
            "newTitle": "My Awesome Drive"
        }
    ]
}'


# {
# }

# The new title is observed when we browse the collections one more time

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/collections/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "54123",
#             "specification": {
#                 "title": "My Awesome Drive",
#                 "product": {
#                     "id": "example-ssd",
#                     "category": "example-ssd",
#                     "provider": "example"
#                 }
#             },
#             "createdAt": 1635151675465,
#             "status": {
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
#             },
#             "providerGeneratedId": null
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

![](/docs/diagrams/files.collections_rename.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#filecollectionincludeflags'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#filecollection'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#filecollectionincludeflags'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='#filecollection'>FileCollection</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='#fssupport'>FSSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

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
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#filecollectionincludeflags'>FileCollectionIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#filecollection'>FileCollection</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filecollection.spec'>FileCollection.Spec</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



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


### `rename`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filecollectionsrenamerequestitem'>FileCollectionsRenameRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FSCollectionSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which `FileCollection` operations are supported for a product_

```kotlin
data class FSCollectionSupport(
    val aclModifiable: Boolean?,
    val usersCanCreate: Boolean?,
    val usersCanDelete: Boolean?,
    val usersCanRename: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>aclModifiable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usersCanCreate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usersCanDelete</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>usersCanRename</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `FSFileSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which file-level operations a product supports_

```kotlin
data class FSFileSupport(
    val aclModifiable: Boolean?,
    val trashSupported: Boolean?,
    val isReadOnly: Boolean?,
    val searchSupported: Boolean?,
    val streamingSearchSupported: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>aclModifiable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>trashSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isReadOnly</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>searchSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Declares support for the normal search endpoint
</summary>



NOTE(Dan, 01/09/2022): For backwards compatibility, this is true by default, however, this will likely change 
to false in a later release. Providers should explicltly declare support for this endpoint for the time being.


</details>

<details>
<summary>
<code>streamingSearchSupported</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Declares support for the streamingSearch endpoint
</summary>





</details>



</details>



---

### `FSProductStatsSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Declares which stats a given product supports_

```kotlin
data class FSProductStatsSupport(
    val sizeInBytes: Boolean?,
    val sizeIncludingChildrenInBytes: Boolean?,
    val modifiedAt: Boolean?,
    val createdAt: Boolean?,
    val accessedAt: Boolean?,
    val unixPermissions: Boolean?,
    val unixOwner: Boolean?,
    val unixGroup: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>sizeInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>sizeIncludingChildrenInBytes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>accessedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixPermissions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixOwner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unixGroup</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `FSSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FSSupport(
    val product: ProductReference,
    val stats: FSProductStatsSupport?,
    val collection: FSCollectionSupport?,
    val files: FSFileSupport?,
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
<code>stats</code>: <code><code><a href='#fsproductstatssupport'>FSProductStatsSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>collection</code>: <code><code><a href='#fscollectionsupport'>FSCollectionSupport</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>files</code>: <code><code><a href='#fsfilesupport'>FSFileSupport</a>?</code></code>
</summary>





</details>



</details>



---

### `FileCollection`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


__

```kotlin
data class FileCollection(
    val id: String,
    val specification: FileCollection.Spec,
    val createdAt: Long,
    val status: FileCollection.Status,
    val updates: List<FileCollection.Update>,
    val owner: ResourceOwner,
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
<code>specification</code>: <code><code><a href='#filecollection.spec'>FileCollection.Spec</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#filecollection.status'>FileCollection.Status</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#filecollection.update'>FileCollection.Update</a>&gt;</code></code> Contains a list of updates from the provider as well as UCloud
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

### `FileCollection.Spec`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Spec(
    val title: String,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product which backs this `Resource`
</summary>





</details>



</details>



---

### `FileCollection.Status`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class Status(
    val resolvedSupport: ResolvedSupport<Product.Storage, FSSupport>?,
    val resolvedProduct: Product.Storage?,
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
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>, <a href='#fssupport'>FSSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Storage.md'>Product.Storage</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `FileCollection.Update`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes an update to the `Resource`_

```kotlin
data class Update(
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

### `FileCollectionIncludeFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileCollectionIncludeFlags(
    val filterMemberFiles: MemberFilesFilter?,
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
<code>filterMemberFiles</code>: <code><code><a href='#memberfilesfilter'>MemberFilesFilter</a>?</code></code>
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

### `MemberFilesFilter`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Filter for member files._

```kotlin
enum class MemberFilesFilter {
    SHOW_ONLY_MINE,
    SHOW_ONLY_MEMBER_FILES,
    DONT_FILTER_COLLECTIONS,
}
```
A member files collection must use the following format to be recognized: "Member Files: $username"

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>SHOW_ONLY_MINE</code> Shows only the requesting user's personal member file along with all other collections
</summary>





</details>

<details>
<summary>
<code>SHOW_ONLY_MEMBER_FILES</code> Shows only the member file collections and hides all others
</summary>





</details>

<details>
<summary>
<code>DONT_FILTER_COLLECTIONS</code> Applies no filter and shows both normal collections and member files
</summary>





</details>



</details>



---

### `FileCollectionsRenameRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileCollectionsRenameRequestItem(
    val id: String,
    val newTitle: String,
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
<code>newTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

