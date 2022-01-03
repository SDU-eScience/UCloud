[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# Example: Browsing the catalog

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


