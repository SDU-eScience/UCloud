[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# Example: Searching for data

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


