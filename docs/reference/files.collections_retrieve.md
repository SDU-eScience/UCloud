[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Drives (FileCollection)](/docs/developer-guide/orchestration/storage/filecollections.md)

# Example: An example collection

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


