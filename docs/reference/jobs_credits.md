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

AccountingV2.browseWallets.call(
    AccountingV2.BrowseWallets.Request(
        childrenQuery = null, 
        consistency = null, 
        filterType = null, 
        includeChildren = false, 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(WalletV2(
        allocationGroups = listOf(AllocationGroupWithParent(
            group = AllocationGroup(
                allocations = listOf(AllocationGroup.Alloc(
                    endDate = 1664865776235, 
                    grantedIn = null, 
                    id = 12541154, 
                    quota = 500000000, 
                    retiredUsage = null, 
                    startDate = 1633329776235, 
                )), 
                id = 1, 
                usage = 499000000, 
            ), 
            parent = ParentOrChildWallet(
                pi = "user", 
                projectId = null, 
                projectTitle = "Root", 
            ), 
        )), 
        children = null, 
        lastSignificantUpdateAt = 0, 
        localUsage = 499000000, 
        maxUsable = 100000, 
        owner = WalletOwner.User(
            username = "user", 
        ), 
        paysFor = ProductCategory(
            accountingFrequency = AccountingFrequency.PERIODIC_MINUTE, 
            accountingUnit = AccountingUnit(
                displayFrequencySuffix = false, 
                floatingPoint = true, 
                name = "DKK", 
                namePlural = "DKK", 
            ), 
            allowSubAllocations = true, 
            freeToUse = false, 
            name = "example-compute", 
            productType = ProductType.COMPUTE, 
            provider = "example", 
        ), 
        quota = 500000000, 
        totalAllocated = 0, 
        totalUsage = 499000000, 
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
        restartOnExit = null, 
        sshEnabled = null, 
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
        restartOnExit = null, 
        sshEnabled = null, 
        timeAllocation = null, 
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
        timestamp = 1633588976235, 
    ), JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
        newTimeAllocation = null, 
        outputFolder = null, 
        state = JobState.RUNNING, 
        status = "Your job is now running!", 
        timestamp = 1633588981235, 
    ), JobUpdate(
        allowRestart = null, 
        expectedDifferentState = null, 
        expectedState = null, 
        newMounts = null, 
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
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/accounting/v2/browseWallets?includeChildren=false" 

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
#                 "provider": "example",
#                 "productType": "COMPUTE",
#                 "accountingUnit": {
#                     "name": "DKK",
#                     "namePlural": "DKK",
#                     "floatingPoint": true,
#                     "displayFrequencySuffix": false
#                 },
#                 "accountingFrequency": "PERIODIC_MINUTE",
#                 "freeToUse": false,
#                 "allowSubAllocations": true
#             },
#             "allocationGroups": [
#                 {
#                     "parent": {
#                         "projectId": null,
#                         "projectTitle": "Root",
#                         "pi": "user"
#                     },
#                     "group": {
#                         "id": 1,
#                         "allocations": [
#                             {
#                                 "id": 12541154,
#                                 "startDate": 1633329776235,
#                                 "endDate": 1664865776235,
#                                 "quota": 500000000,
#                                 "grantedIn": null,
#                                 "retiredUsage": null
#                             }
#                         ],
#                         "usage": 499000000
#                     }
#                 }
#             ],
#             "children": null,
#             "totalUsage": 499000000,
#             "localUsage": 499000000,
#             "maxUsable": 100000,
#             "quota": 500000000,
#             "totalAllocated": 0,
#             "lastSignificantUpdateAt": 0
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
            "openedFile": null,
            "restartOnExit": null,
            "sshEnabled": null
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
#             "allowRestart": null,
#             "newMounts": null,
#             "timestamp": 1633588976235
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
#             "timestamp": 1633588981235
#         },
#         {
#             "state": "SUCCESS",
#             "outputFolder": null,
#             "status": "Your job has been terminated (No more credits)",
#             "expectedState": null,
#             "expectedDifferentState": null,
#             "newTimeAllocation": null,
#             "allowRestart": null,
#             "newMounts": null,
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
#         "openedFile": null,
#         "restartOnExit": null,
#         "sshEnabled": null
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


