[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# Example: Browsing the catalog with a filter

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
        sortDirection = null, 
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
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/example/browse?filterState=RUNNING&includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false" 

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


