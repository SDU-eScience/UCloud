[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# Example: Resource Collaboration

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
404 Not Found
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

# 404 Not Found

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


