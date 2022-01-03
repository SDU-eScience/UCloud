[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Introduction to Resources](/docs/developer-guide/orchestration/resources.md)

# Example: Creating and retrieving a resource

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


