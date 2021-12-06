[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Introduction to Resources API for Providers](/docs/developer-guide/orchestration/storage/providers/resources.md)

# Example: Creation of Resources

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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we show a simple creation request. The creation request is always initiated by a 
user. */

// Authenticated as ucloud
await callAPI(ExampleProviderPROVIDERIDApi.create(
    {
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
    }
);

/*
{
    "responses": [
        null
    ]
}
*/

/* In this case, the provider decided not to attach a provider generated ID. */


/* The provider can, at a later point in time, retrieve this resource from UCloud/Core. */

// Authenticated as provider
await callAPI(ExampleControlApi.retrieve(
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


