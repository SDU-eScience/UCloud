[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Shares](/docs/developer-guide/orchestration/storage/shares.md)

# Example: Complete example

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

/* In this example we will see Alice sharing a folder with Bob. Alice starts by creating a share. The
share references a UFile. */

Shares.create.call(
    bulkRequestOf(Share.Spec(
        permissions = listOf(Permission.EDIT), 
        product = ProductReference(
            category = "example-ssd", 
            id = "example-ssd", 
            provider = "example", 
        ), 
        sharedWith = "bob", 
        sourceFilePath = "/5123/work/my-project/my-collaboration", 
    )),
    alice
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "6342", 
    )), 
)
*/

/* This returns a new ID of the Share resource. Bob can now view this when browsing the ingoing shares. */

Shares.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = ShareFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterIngoing = true, 
            filterOriginalPath = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            filterRejected = null, 
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
    bob
).orThrow()

/*
PageV2(
    items = listOf(Share(
        createdAt = 1635151675465, 
        id = "6342", 
        owner = ResourceOwner(
            createdBy = "alice", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.READ), 
            others = null, 
        ), 
        specification = Share.Spec(
            permissions = listOf(Permission.EDIT), 
            product = ProductReference(
                category = "example-ssd", 
                id = "example-ssd", 
                provider = "example", 
            ), 
            sharedWith = "bob", 
            sourceFilePath = "/5123/work/my-project/my-collaboration", 
        ), 
        status = Share.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            shareAvailableAt = null, 
            state = State.PENDING, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "6342", 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* Bob now approves this share request */

Shares.approve.call(
    bulkRequestOf(FindByStringId(
        id = "6342", 
    )),
    bob
).orThrow()

/*
Unit
*/

/* And the file is now shared and available at the path /6412 */

Shares.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = ShareFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterIngoing = true, 
            filterOriginalPath = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            filterRejected = null, 
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
    bob
).orThrow()

/*
PageV2(
    items = listOf(Share(
        createdAt = 1635151675465, 
        id = "6342", 
        owner = ResourceOwner(
            createdBy = "alice", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.READ), 
            others = null, 
        ), 
        specification = Share.Spec(
            permissions = listOf(Permission.EDIT), 
            product = ProductReference(
                category = "example-ssd", 
                id = "example-ssd", 
                provider = "example", 
            ), 
            sharedWith = "bob", 
            sourceFilePath = "/5123/work/my-project/my-collaboration", 
        ), 
        status = Share.Status(
            resolvedProduct = null, 
            resolvedSupport = null, 
            shareAvailableAt = "/6412", 
            state = State.APPROVED, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "6342", 
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

# In this example we will see Alice sharing a folder with Bob. Alice starts by creating a share. The
# share references a UFile.

# Authenticated as alice
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/shares" -d '{
    "items": [
        {
            "sharedWith": "bob",
            "sourceFilePath": "/5123/work/my-project/my-collaboration",
            "permissions": [
                "EDIT"
            ],
            "product": {
                "id": "example-ssd",
                "category": "example-ssd",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "6342"
#         }
#     ]
# }

# This returns a new ID of the Share resource. Bob can now view this when browsing the ingoing shares.

# Authenticated as bob
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/shares/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&filterIngoing=true&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "6342",
#             "specification": {
#                 "sharedWith": "bob",
#                 "sourceFilePath": "/5123/work/my-project/my-collaboration",
#                 "permissions": [
#                     "EDIT"
#                 ],
#                 "product": {
#                     "id": "example-ssd",
#                     "category": "example-ssd",
#                     "provider": "example"
#                 }
#             },
#             "createdAt": 1635151675465,
#             "status": {
#                 "shareAvailableAt": null,
#                 "state": "PENDING",
#                 "resolvedSupport": null,
#                 "resolvedProduct": null
#             },
#             "updates": [
#             ],
#             "owner": {
#                 "createdBy": "alice",
#                 "project": null
#             },
#             "permissions": {
#                 "myself": [
#                     "READ"
#                 ],
#                 "others": null
#             }
#         }
#     ],
#     "next": null
# }

# Bob now approves this share request

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/shares/approve" -d '{
    "items": [
        {
            "id": "6342"
        }
    ]
}'


# {
# }

# And the file is now shared and available at the path /6412

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/shares/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&filterIngoing=true&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "6342",
#             "specification": {
#                 "sharedWith": "bob",
#                 "sourceFilePath": "/5123/work/my-project/my-collaboration",
#                 "permissions": [
#                     "EDIT"
#                 ],
#                 "product": {
#                     "id": "example-ssd",
#                     "category": "example-ssd",
#                     "provider": "example"
#                 }
#             },
#             "createdAt": 1635151675465,
#             "status": {
#                 "shareAvailableAt": "/6412",
#                 "state": "APPROVED",
#                 "resolvedSupport": null,
#                 "resolvedProduct": null
#             },
#             "updates": [
#             ],
#             "owner": {
#                 "createdBy": "alice",
#                 "project": null
#             },
#             "permissions": {
#                 "myself": [
#                     "READ"
#                 ],
#                 "others": null
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

![](/docs/diagrams/shares_complete.png)

</details>


