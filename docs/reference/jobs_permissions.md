[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Losing access to resources

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

/* In this example, the user will create a Job using shared resources. Later in the example, the user
will lose access to these resources. */


/* When the user starts the Job, they have access to some shared files. These are used in theJob (see the resources section). */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-web-application", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = null, 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = listOf(AppParameterValue.File(
            path = "/12512/shared", 
            readOnly = false, 
        )), 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "62348", 
    )), 
)
*/

/* The Job is now running */


/* However, a few minutes later the share is revoked. UCloud automatically kills the Job a few minutes
after this. The status now reflects this. */

Jobs.retrieve.call(
    ResourceRetrieveRequest(
        flags = JobIncludeFlags(
            filterApplication = null, 
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
            includeApplication = null, 
            includeOthers = false, 
            includeParameters = null, 
            includeProduct = false, 
            includeSupport = false, 
            includeUpdates = false, 
        ), 
        id = "62348", 
    ),
    user
).orThrow()

/*
Job(
    createdAt = 1633588976235, 
    id = "62348", 
    output = null, 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = null, 
    specification = JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-web-application", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = null, 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = listOf(AppParameterValue.File(
            path = "/12512/shared", 
            readOnly = false, 
        )), 
        timeAllocation = null, 
    ), 
    status = JobStatus(
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.SUCCESS, 
    ), 
    updates = listOf(JobUpdate(
        expectedDifferentState = null, 
        expectedState = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.IN_QUEUE, 
        status = "Your job is now waiting in the queue!", 
        timestamp = 1633588976235, 
    ), JobUpdate(
        expectedDifferentState = null, 
        expectedState = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.RUNNING, 
        status = "Your job is now running!", 
        timestamp = 1633588981235, 
    ), JobUpdate(
        expectedDifferentState = null, 
        expectedState = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.SUCCESS, 
        status = "Your job has been terminated (Lost permissions)", 
        timestamp = 1633589101235, 
    )), 
    providerGeneratedId = "62348", 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, the user will create a Job using shared resources. Later in the example, the user
will lose access to these resources. */


/* When the user starts the Job, they have access to some shared files. These are used in theJob (see the resources section). */

// Authenticated as user
await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-web-application",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": null,
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": null,
                "resources": [
                    {
                        "type": "file",
                        "path": "/12512/shared",
                        "readOnly": false
                    }
                ],
                "timeAllocation": null,
                "openedFile": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "62348"
        }
    ]
}
*/

/* The Job is now running */


/* However, a few minutes later the share is revoked. UCloud automatically kills the Job a few minutes
after this. The status now reflects this. */

await callAPI(JobsApi.retrieve(
    {
        "flags": {
            "filterApplication": null,
            "filterState": null,
            "includeParameters": null,
            "includeApplication": null,
            "includeProduct": false,
            "includeOthers": false,
            "includeUpdates": false,
            "includeSupport": false,
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
        "id": "62348"
    }
);

/*
{
    "id": "62348",
    "owner": {
        "createdBy": "user",
        "project": null
    },
    "updates": [
        {
            "state": "IN_QUEUE",
            "outputFolder": null,
            "status": "Your job is now waiting in the queue!",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "timestamp": 1633588976235
        },
        {
            "state": "RUNNING",
            "outputFolder": null,
            "status": "Your job is now running!",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "timestamp": 1633588981235
        },
        {
            "state": "SUCCESS",
            "outputFolder": null,
            "status": "Your job has been terminated (Lost permissions)",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "timestamp": 1633589101235
        }
    ],
    "specification": {
        "application": {
            "name": "acme-web-application",
            "version": "1.0.0"
        },
        "product": {
            "id": "example-compute",
            "category": "example-compute",
            "provider": "example"
        },
        "name": null,
        "replicas": 1,
        "allowDuplicateJob": false,
        "parameters": null,
        "resources": [
            {
                "type": "file",
                "path": "/12512/shared",
                "readOnly": false
            }
        ],
        "timeAllocation": null,
        "openedFile": null
    },
    "status": {
        "state": "SUCCESS",
        "jobParametersJson": null,
        "startedAt": null,
        "expiresAt": null,
        "resolvedApplication": null,
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "createdAt": 1633588976235,
    "output": null,
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

# In this example, the user will create a Job using shared resources. Later in the example, the user
# will lose access to these resources.

# When the user starts the Job, they have access to some shared files. These are used in theJob (see the resources section).

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-web-application",
                "version": "1.0.0"
            },
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            },
            "name": null,
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": null,
            "resources": [
                {
                    "type": "file",
                    "path": "/12512/shared",
                    "readOnly": false
                }
            ],
            "timeAllocation": null,
            "openedFile": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "62348"
#         }
#     ]
# }

# The Job is now running

# However, a few minutes later the share is revoked. UCloud automatically kills the Job a few minutes
# after this. The status now reflects this.

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/jobs/retrieve?includeProduct=false&includeOthers=false&includeUpdates=false&includeSupport=false&id=62348" 

# {
#     "id": "62348",
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "updates": [
#         {
#             "state": "IN_QUEUE",
#             "outputFolder": null,
#             "status": "Your job is now waiting in the queue!",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "timestamp": 1633588976235
#         },
#         {
#             "state": "RUNNING",
#             "outputFolder": null,
#             "status": "Your job is now running!",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "timestamp": 1633588981235
#         },
#         {
#             "state": "SUCCESS",
#             "outputFolder": null,
#             "status": "Your job has been terminated (Lost permissions)",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "timestamp": 1633589101235
#         }
#     ],
#     "specification": {
#         "application": {
#             "name": "acme-web-application",
#             "version": "1.0.0"
#         },
#         "product": {
#             "id": "example-compute",
#             "category": "example-compute",
#             "provider": "example"
#         },
#         "name": null,
#         "replicas": 1,
#         "allowDuplicateJob": false,
#         "parameters": null,
#         "resources": [
#             {
#                 "type": "file",
#                 "path": "/12512/shared",
#                 "readOnly": false
#             }
#         ],
#         "timeAllocation": null,
#         "openedFile": null
#     },
#     "status": {
#         "state": "SUCCESS",
#         "jobParametersJson": null,
#         "startedAt": null,
#         "expiresAt": null,
#         "resolvedApplication": null,
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "createdAt": 1633588976235,
#     "output": null,
#     "permissions": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_permissions.png)

</details>


