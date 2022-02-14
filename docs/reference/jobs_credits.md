[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Running out of compute credits

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

/* In this example, the user will create a Job and eventually run out of compute credits. */


/* When the user creates the Job, they have enough credits */

Wallets.browse.call(
    WalletBrowseRequest(
        consistency = null, 
        filterType = null, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(Wallet(
        allocations = listOf(WalletAllocation(
            allocationPath = listOf("1254151"), 
            balance = 500, 
            endDate = null, 
            grantedIn = 2, 
            id = "1254151", 
            initialBalance = 500000000, 
            localBalance = 500, 
            startDate = 1633329776235, 
        )), 
        chargePolicy = AllocationSelectorPolicy.EXPIRE_FIRST, 
        chargeType = ChargeType.ABSOLUTE, 
        owner = WalletOwner.User(
            username = "user", 
        ), 
        paysFor = ProductCategoryId(
            id = "example-compute", 
            name = "example-compute", 
            provider = "example", 
        ), 
        productType = ProductType.COMPUTE, 
        unit = ProductPriceUnit.CREDITS_PER_MINUTE, 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* 📝 Note: at this point the user has a very low amount of credits remaining.
It will only last a couple of minutes. */

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


/* However, a few minutes later the Job is automatically killed by UCloud. The status now reflects this. */

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
        resources = null, 
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
        status = "Your job has been terminated (No more credits)", 
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

/* In this example, the user will create a Job and eventually run out of compute credits. */


/* When the user creates the Job, they have enough credits */

// Authenticated as user
await callAPI(AccountingWalletsApi.browse(
    {
        "itemsPerPage": null,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterType": null
    }
);

/*
{
    "itemsPerPage": 50,
    "items": [
        {
            "owner": {
                "type": "user",
                "username": "user"
            },
            "paysFor": {
                "name": "example-compute",
                "provider": "example"
            },
            "allocations": [
                {
                    "id": "1254151",
                    "allocationPath": [
                        "1254151"
                    ],
                    "balance": 500,
                    "initialBalance": 500000000,
                    "localBalance": 500,
                    "startDate": 1633329776235,
                    "endDate": null,
                    "grantedIn": 2
                }
            ],
            "chargePolicy": "EXPIRE_FIRST",
            "productType": "COMPUTE",
            "chargeType": "ABSOLUTE",
            "unit": "CREDITS_PER_MINUTE"
        }
    ],
    "next": null
}
*/

/* 📝 Note: at this point the user has a very low amount of credits remaining.
It will only last a couple of minutes. */

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


/* However, a few minutes later the Job is automatically killed by UCloud. The status now reflects this. */

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
            "status": "Your job has been terminated (No more credits)",
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
        "resources": null,
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

# In this example, the user will create a Job and eventually run out of compute credits.

# When the user creates the Job, they have enough credits

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/wallets/browse?" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "owner": {
#                 "type": "user",
#                 "username": "user"
#             },
#             "paysFor": {
#                 "name": "example-compute",
#                 "provider": "example"
#             },
#             "allocations": [
#                 {
#                     "id": "1254151",
#                     "allocationPath": [
#                         "1254151"
#                     ],
#                     "balance": 500,
#                     "initialBalance": 500000000,
#                     "localBalance": 500,
#                     "startDate": 1633329776235,
#                     "endDate": null,
#                     "grantedIn": 2
#                 }
#             ],
#             "chargePolicy": "EXPIRE_FIRST",
#             "productType": "COMPUTE",
#             "chargeType": "ABSOLUTE",
#             "unit": "CREDITS_PER_MINUTE"
#         }
#     ],
#     "next": null
# }

# 📝 Note: at this point the user has a very low amount of credits remaining.
# It will only last a couple of minutes.

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

# However, a few minutes later the Job is automatically killed by UCloud. The status now reflects this.

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
#             "status": "Your job has been terminated (No more credits)",
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
#         "resources": null,
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

![](/docs/diagrams/jobs_credits.png)

</details>


