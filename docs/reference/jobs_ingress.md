[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Starting a Job with a public link (Ingress)

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

/* In this example, the user will create a Job which exposes a web-interface. This web-interface will
become available through a publicly accessible link. */


/* First, the user creates an Ingress resource (this needs to be done once per ingress) */

Ingresses.create.call(
    bulkRequestOf(IngressSpecification(
        domain = "app-my-application.provider.example.com", 
        product = ProductReference(
            category = "example-ingress", 
            id = "example-ingress", 
            provider = "example", 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "41231", 
    )), 
)
*/

/* This link can now be attached to any Application which support a web-interface */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-web-app", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = null, 
        product = ProductReference(
            category = "compute-example", 
            id = "compute-example", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = listOf(AppParameterValue.Ingress(
            id = "41231", 
        )), 
        restartOnExit = null, 
        sshEnabled = null, 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "41252", 
    )), 
)
*/

/* The Application is now running, and we can access it through the public link */


/* The Ingress will also remain exclusively bound to the Job. It will remain like this until the Job
terminates. You can check the status of the Ingress simply by retrieving it. */

Ingresses.retrieve.call(
    ResourceRetrieveRequest(
        flags = IngressIncludeFlags(
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
        id = "41231", 
    ),
    user
).orThrow()

/*
Ingress(
    createdAt = 1633087693694, 
    id = "41231", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = null, 
    specification = IngressSpecification(
        domain = "app-my-application.provider.example.com", 
        product = ProductReference(
            category = "example-ingress", 
            id = "example-ingress", 
            provider = "example", 
        ), 
    ), 
    status = IngressStatus(
        boundTo = listOf("41231"), 
        resolvedProduct = null, 
        resolvedSupport = null, 
        state = IngressState.READY, 
    ), 
    updates = emptyList(), 
    providerGeneratedId = "41231", 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, the user will create a Job which exposes a web-interface. This web-interface will
become available through a publicly accessible link. */


/* First, the user creates an Ingress resource (this needs to be done once per ingress) */

// Authenticated as user
await callAPI(IngressesApi.create(
    {
        "items": [
            {
                "domain": "app-my-application.provider.example.com",
                "product": {
                    "id": "example-ingress",
                    "category": "example-ingress",
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
            "id": "41231"
        }
    ]
}
*/

/* This link can now be attached to any Application which support a web-interface */

await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-web-app",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "compute-example",
                    "category": "compute-example",
                    "provider": "example"
                },
                "name": null,
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": null,
                "resources": [
                    {
                        "type": "ingress",
                        "id": "41231"
                    }
                ],
                "timeAllocation": null,
                "openedFile": null,
                "restartOnExit": null,
                "sshEnabled": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "41252"
        }
    ]
}
*/

/* The Application is now running, and we can access it through the public link */


/* The Ingress will also remain exclusively bound to the Job. It will remain like this until the Job
terminates. You can check the status of the Ingress simply by retrieving it. */

await callAPI(IngressesApi.retrieve(
    {
        "flags": {
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
            "filterState": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
        },
        "id": "41231"
    }
);

/*
{
    "id": "41231",
    "specification": {
        "domain": "app-my-application.provider.example.com",
        "product": {
            "id": "example-ingress",
            "category": "example-ingress",
            "provider": "example"
        }
    },
    "owner": {
        "createdBy": "user",
        "project": null
    },
    "createdAt": 1633087693694,
    "status": {
        "boundTo": [
            "41231"
        ],
        "state": "READY",
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "updates": [
    ],
    "permissions": null
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

# In this example, the user will create a Job which exposes a web-interface. This web-interface will
# become available through a publicly accessible link.

# First, the user creates an Ingress resource (this needs to be done once per ingress)

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/ingresses" -d '{
    "items": [
        {
            "domain": "app-my-application.provider.example.com",
            "product": {
                "id": "example-ingress",
                "category": "example-ingress",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "41231"
#         }
#     ]
# }

# This link can now be attached to any Application which support a web-interface

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-web-app",
                "version": "1.0.0"
            },
            "product": {
                "id": "compute-example",
                "category": "compute-example",
                "provider": "example"
            },
            "name": null,
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": null,
            "resources": [
                {
                    "type": "ingress",
                    "id": "41231"
                }
            ],
            "timeAllocation": null,
            "openedFile": null,
            "restartOnExit": null,
            "sshEnabled": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "41252"
#         }
#     ]
# }

# The Application is now running, and we can access it through the public link

# The Ingress will also remain exclusively bound to the Job. It will remain like this until the Job
# terminates. You can check the status of the Ingress simply by retrieving it.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/ingresses/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&id=41231" 

# {
#     "id": "41231",
#     "specification": {
#         "domain": "app-my-application.provider.example.com",
#         "product": {
#             "id": "example-ingress",
#             "category": "example-ingress",
#             "provider": "example"
#         }
#     },
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "createdAt": 1633087693694,
#     "status": {
#         "boundTo": [
#             "41231"
#         ],
#         "state": "READY",
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "updates": [
#     ],
#     "permissions": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_ingress.png)

</details>


