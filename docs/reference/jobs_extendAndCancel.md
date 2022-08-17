[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Extending a Job and terminating it early

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>The provider must support the extension API</li>
</ul></td></tr>
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

/* In this example we will show how a user can extend the duration of a Job. Later in the same
example, we show how the user can cancel it early. */

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
        resources = null, 
        restartOnExit = null, 
        timeAllocation = SimpleDuration(
            hours = 5, 
            minutes = 0, 
            seconds = 0, 
        ), 
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

/* The Job is initially allocated with a duration of 5 hours. We can check when it expires by retrieving the Job */

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
    createdAt = 1633329776235, 
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
        resources = null, 
        restartOnExit = null, 
        timeAllocation = SimpleDuration(
            hours = 5, 
            minutes = 0, 
            seconds = 0, 
        ), 
    ), 
    status = JobStatus(
        allowRestart = false, 
        expiresAt = 1633347776235, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.RUNNING, 
    ), 
    updates = listOf(JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.IN_QUEUE, 
        status = "Your job is now waiting in the queue!", 
        timestamp = 1633329776235, 
    ), JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.RUNNING, 
        status = "Your job is now running!", 
        timestamp = 1633329781235, 
    )), 
    providerGeneratedId = "62348", 
)
*/

/* We can extend the duration quite easily */

Jobs.extend.call(
    bulkRequestOf(JobsExtendRequestItem(
        jobId = "62348", 
        requestedTime = SimpleDuration(
            hours = 1, 
            minutes = 0, 
            seconds = 0, 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(Unit), 
)
*/

/* The new expiration is reflected if we retrieve it again */

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
    createdAt = 1633329776235, 
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
        resources = null, 
        restartOnExit = null, 
        timeAllocation = SimpleDuration(
            hours = 5, 
            minutes = 0, 
            seconds = 0, 
        ), 
    ), 
    status = JobStatus(
        allowRestart = false, 
        expiresAt = 1633351376235, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.RUNNING, 
    ), 
    updates = listOf(JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.IN_QUEUE, 
        status = "Your job is now waiting in the queue!", 
        timestamp = 1633329776235, 
    ), JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.RUNNING, 
        status = "Your job is now running!", 
        timestamp = 1633329781235, 
    )), 
    providerGeneratedId = "62348", 
)
*/

/* If the user decides that they are done with the Job early, then they can simply terminate it */

Jobs.terminate.call(
    bulkRequestOf(FindByStringId(
        id = "62348", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(Unit), 
)
*/

/* This termination is reflected in the status (and updates) */

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
    createdAt = 1633329776235, 
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
        resources = null, 
        restartOnExit = null, 
        timeAllocation = SimpleDuration(
            hours = 5, 
            minutes = 0, 
            seconds = 0, 
        ), 
    ), 
    status = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.SUCCESS, 
    ), 
    updates = listOf(JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.IN_QUEUE, 
        status = "Your job is now waiting in the queue!", 
        timestamp = 1633329776235, 
    ), JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.RUNNING, 
        status = "Your job is now running!", 
        timestamp = 1633329781235, 
    ), JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.SUCCESS, 
        status = "Your job has been cancelled!", 
        timestamp = 1633336981235, 
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

/* In this example we will show how a user can extend the duration of a Job. Later in the same
example, we show how the user can cancel it early. */

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
                "resources": null,
                "timeAllocation": {
                    "hours": 5,
                    "minutes": 0,
                    "seconds": 0
                },
                "openedFile": null,
                "restartOnExit": null
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

/* The Job is initially allocated with a duration of 5 hours. We can check when it expires by retrieving the Job */

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
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633329776235
        },
        {
            "state": "RUNNING",
            "outputFolder": null,
            "status": "Your job is now running!",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633329781235
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
        "resources": null,
        "timeAllocation": {
            "hours": 5,
            "minutes": 0,
            "seconds": 0
        },
        "openedFile": null,
        "restartOnExit": null
    },
    "status": {
        "state": "RUNNING",
        "jobParametersJson": null,
        "startedAt": null,
        "expiresAt": 1633347776235,
        "resolvedApplication": null,
        "resolvedSupport": null,
        "resolvedProduct": null,
        "allowRestart": false
    },
    "createdAt": 1633329776235,
    "output": null,
    "permissions": null
}
*/

/* We can extend the duration quite easily */

await callAPI(JobsApi.extend(
    {
        "items": [
            {
                "jobId": "62348",
                "requestedTime": {
                    "hours": 1,
                    "minutes": 0,
                    "seconds": 0
                }
            }
        ]
    }
);

/*
{
    "responses": [
        {
        }
    ]
}
*/

/* The new expiration is reflected if we retrieve it again */

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
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633329776235
        },
        {
            "state": "RUNNING",
            "outputFolder": null,
            "status": "Your job is now running!",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633329781235
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
        "resources": null,
        "timeAllocation": {
            "hours": 5,
            "minutes": 0,
            "seconds": 0
        },
        "openedFile": null,
        "restartOnExit": null
    },
    "status": {
        "state": "RUNNING",
        "jobParametersJson": null,
        "startedAt": null,
        "expiresAt": 1633351376235,
        "resolvedApplication": null,
        "resolvedSupport": null,
        "resolvedProduct": null,
        "allowRestart": false
    },
    "createdAt": 1633329776235,
    "output": null,
    "permissions": null
}
*/

/* If the user decides that they are done with the Job early, then they can simply terminate it */

await callAPI(JobsApi.terminate(
    {
        "items": [
            {
                "id": "62348"
            }
        ]
    }
);

/*
{
    "responses": [
        {
        }
    ]
}
*/

/* This termination is reflected in the status (and updates) */

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
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633329776235
        },
        {
            "state": "RUNNING",
            "outputFolder": null,
            "status": "Your job is now running!",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633329781235
        },
        {
            "state": "SUCCESS",
            "outputFolder": null,
            "status": "Your job has been cancelled!",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633336981235
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
        "resources": null,
        "timeAllocation": {
            "hours": 5,
            "minutes": 0,
            "seconds": 0
        },
        "openedFile": null,
        "restartOnExit": null
    },
    "status": {
        "state": "SUCCESS",
        "jobParametersJson": null,
        "startedAt": null,
        "expiresAt": null,
        "resolvedApplication": null,
        "resolvedSupport": null,
        "resolvedProduct": null,
        "allowRestart": false
    },
    "createdAt": 1633329776235,
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

# In this example we will show how a user can extend the duration of a Job. Later in the same
# example, we show how the user can cancel it early.

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
            "resources": null,
            "timeAllocation": {
                "hours": 5,
                "minutes": 0,
                "seconds": 0
            },
            "openedFile": null,
            "restartOnExit": null
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

# The Job is initially allocated with a duration of 5 hours. We can check when it expires by retrieving the Job

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
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633329776235
#         },
#         {
#             "state": "RUNNING",
#             "outputFolder": null,
#             "status": "Your job is now running!",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633329781235
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
#         "resources": null,
#         "timeAllocation": {
#             "hours": 5,
#             "minutes": 0,
#             "seconds": 0
#         },
#         "openedFile": null,
#         "restartOnExit": null
#     },
#     "status": {
#         "state": "RUNNING",
#         "jobParametersJson": null,
#         "startedAt": null,
#         "expiresAt": 1633347776235,
#         "resolvedApplication": null,
#         "resolvedSupport": null,
#         "resolvedProduct": null,
#         "allowRestart": false
#     },
#     "createdAt": 1633329776235,
#     "output": null,
#     "permissions": null
# }

# We can extend the duration quite easily

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/extend" -d '{
    "items": [
        {
            "jobId": "62348",
            "requestedTime": {
                "hours": 1,
                "minutes": 0,
                "seconds": 0
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#         }
#     ]
# }

# The new expiration is reflected if we retrieve it again

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
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633329776235
#         },
#         {
#             "state": "RUNNING",
#             "outputFolder": null,
#             "status": "Your job is now running!",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633329781235
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
#         "resources": null,
#         "timeAllocation": {
#             "hours": 5,
#             "minutes": 0,
#             "seconds": 0
#         },
#         "openedFile": null,
#         "restartOnExit": null
#     },
#     "status": {
#         "state": "RUNNING",
#         "jobParametersJson": null,
#         "startedAt": null,
#         "expiresAt": 1633351376235,
#         "resolvedApplication": null,
#         "resolvedSupport": null,
#         "resolvedProduct": null,
#         "allowRestart": false
#     },
#     "createdAt": 1633329776235,
#     "output": null,
#     "permissions": null
# }

# If the user decides that they are done with the Job early, then they can simply terminate it

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/terminate" -d '{
    "items": [
        {
            "id": "62348"
        }
    ]
}'


# {
#     "responses": [
#         {
#         }
#     ]
# }

# This termination is reflected in the status (and updates)

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
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633329776235
#         },
#         {
#             "state": "RUNNING",
#             "outputFolder": null,
#             "status": "Your job is now running!",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633329781235
#         },
#         {
#             "state": "SUCCESS",
#             "outputFolder": null,
#             "status": "Your job has been cancelled!",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633336981235
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
#         "resources": null,
#         "timeAllocation": {
#             "hours": 5,
#             "minutes": 0,
#             "seconds": 0
#         },
#         "openedFile": null,
#         "restartOnExit": null
#     },
#     "status": {
#         "state": "SUCCESS",
#         "jobParametersJson": null,
#         "startedAt": null,
#         "expiresAt": null,
#         "resolvedApplication": null,
#         "resolvedSupport": null,
#         "resolvedProduct": null,
#         "allowRestart": false
#     },
#     "createdAt": 1633329776235,
#     "output": null,
#     "permissions": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_extendAndCancel.png)

</details>


