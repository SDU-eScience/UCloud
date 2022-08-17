[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Creating a simple batch Job

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>User has been granted credits for using the selected machine</li>
</ul></td></tr>
<tr><th>Post-conditions</th><td><ul>
<li>A Job is started in the user's workspace</li>
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

/* The user finds an interesting application from the catalog */

val applications = AppStore.listAll.call(
    PaginationRequest(
        itemsPerPage = 50, 
        page = 0, 
    ),
    user
).orThrow()

/*
applications = Page(
    items = listOf(ApplicationSummaryWithFavorite(
        favorite = false, 
        metadata = ApplicationMetadata(
            authors = listOf("UCloud"), 
            description = "This is a batch application", 
            isPublic = true, 
            name = "a-batch-application", 
            public = true, 
            title = "A Batch Application", 
            version = "1.0.0", 
            website = null, 
        ), 
        tags = listOf("very-scientific"), 
    )), 
    itemsInTotal = 1, 
    itemsPerPage = 50, 
    pageNumber = 0, 
)
*/

/* The user selects the first application ('batch' in version '1.0.0') */


/* The user requests additional information about the application */

val application = AppStore.findByNameAndVersion.call(
    FindApplicationAndOptionalDependencies(
        appName = "a-batch-application", 
        appVersion = "1.0.0", 
    ),
    user
).orThrow()

/*
application = ApplicationWithFavoriteAndTags(
    favorite = false, 
    invocation = ApplicationInvocationDescription(
        allowAdditionalMounts = null, 
        allowAdditionalPeers = null, 
        allowMultiNode = false, 
        allowPublicIp = false, 
        allowPublicLink = null, 
        applicationType = ApplicationType.BATCH, 
        container = null, 
        environment = null, 
        fileExtensions = emptyList(), 
        invocation = listOf(WordInvocationParameter(
            word = "batch", 
        ), VariableInvocationParameter(
            isPrefixVariablePartOfArg = false, 
            isSuffixVariablePartOfArg = false, 
            prefixGlobal = "", 
            prefixVariable = "", 
            suffixGlobal = "", 
            suffixVariable = "", 
            variableNames = listOf("var"), 
        )), 
        licenseServers = emptyList(), 
        outputFileGlobs = listOf("*"), 
        parameters = listOf(ApplicationParameter.Text(
            defaultValue = null, 
            description = "An example input variable", 
            name = "var", 
            optional = false, 
            title = "", 
        )), 
        shouldAllowAdditionalMounts = false, 
        shouldAllowAdditionalPeers = true, 
        tool = ToolReference(
            name = "batch-tool", 
            tool = Tool(
                createdAt = 1632979836013, 
                description = NormalizedToolDescription(
                    authors = listOf("UCloud"), 
                    backend = ToolBackend.DOCKER, 
                    container = null, 
                    defaultNumberOfNodes = 1, 
                    defaultTimeAllocation = SimpleDuration(
                        hours = 1, 
                        minutes = 0, 
                        seconds = 0, 
                    ), 
                    description = "Batch tool", 
                    image = "dreg.cloud.sdu.dk/batch/batch:1.0.0", 
                    info = NameAndVersion(
                        name = "batch-tool", 
                        version = "1.0.0", 
                    ), 
                    license = "None", 
                    requiredModules = emptyList(), 
                    supportedProviders = null, 
                    title = "Batch tool", 
                ), 
                modifiedAt = 1632979836013, 
                owner = "user", 
            ), 
            version = "1.0.0", 
        ), 
        vnc = null, 
        web = null, 
    ), 
    metadata = ApplicationMetadata(
        authors = listOf("UCloud"), 
        description = "This is a batch application", 
        isPublic = true, 
        name = "a-batch-application", 
        public = true, 
        title = "A Batch Application", 
        version = "1.0.0", 
        website = null, 
    ), 
    tags = listOf("very-scientific"), 
)
*/

/* The user looks for a suitable machine */

val machineTypes = Products.browse.call(
    ProductsBrowseRequest(
        consistency = null, 
        filterArea = ProductType.COMPUTE, 
        filterCategory = null, 
        filterName = null, 
        filterProvider = null, 
        filterVersion = null, 
        includeBalance = null, 
        itemsPerPage = 50, 
        itemsToSkip = null, 
        next = null, 
        showAllVersions = null, 
    ),
    user
).orThrow()

/*
machineTypes = PageV2(
    items = listOf(Product.Compute(
        category = ProductCategoryId(
            id = "example-compute", 
            name = "example-compute", 
            provider = "example", 
        ), 
        chargeType = ChargeType.ABSOLUTE, 
        cpu = 10, 
        description = "An example compute product", 
        freeToUse = false, 
        gpu = 0, 
        hiddenInGrantApplications = false, 
        memoryInGigs = 20, 
        name = "example-compute", 
        pricePerUnit = 1000000, 
        priority = 0, 
        productType = ProductType.COMPUTE, 
        unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
        version = 1, 
        balance = null, 
        id = "example-compute", 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/

/* The user starts the Job with input based on previous requests */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "a-batch-application", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = mapOf("var" to AppParameterValue.Text(
            value = "Example", 
        )), 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = null, 
        restartOnExit = null, 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "48920", 
    )), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* The user finds an interesting application from the catalog */

// Authenticated as user
const applications = await callAPI(HpcAppsApi.listAll(
    {
        "itemsPerPage": 50,
        "page": 0
    }
);

/*
applications = {
    "itemsInTotal": 1,
    "itemsPerPage": 50,
    "pageNumber": 0,
    "items": [
        {
            "metadata": {
                "name": "a-batch-application",
                "version": "1.0.0",
                "authors": [
                    "UCloud"
                ],
                "title": "A Batch Application",
                "description": "This is a batch application",
                "website": null,
                "public": true
            },
            "favorite": false,
            "tags": [
                "very-scientific"
            ]
        }
    ]
}
*/

/* The user selects the first application ('batch' in version '1.0.0') */


/* The user requests additional information about the application */

const application = await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "a-batch-application",
        "appVersion": "1.0.0"
    }
);

/*
application = {
    "metadata": {
        "name": "a-batch-application",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "A Batch Application",
        "description": "This is a batch application",
        "website": null,
        "public": true
    },
    "invocation": {
        "tool": {
            "name": "batch-tool",
            "version": "1.0.0",
            "tool": {
                "owner": "user",
                "createdAt": 1632979836013,
                "modifiedAt": 1632979836013,
                "description": {
                    "info": {
                        "name": "batch-tool",
                        "version": "1.0.0"
                    },
                    "container": null,
                    "defaultNumberOfNodes": 1,
                    "defaultTimeAllocation": {
                        "hours": 1,
                        "minutes": 0,
                        "seconds": 0
                    },
                    "requiredModules": [
                    ],
                    "authors": [
                        "UCloud"
                    ],
                    "title": "Batch tool",
                    "description": "Batch tool",
                    "backend": "DOCKER",
                    "license": "None",
                    "image": "dreg.cloud.sdu.dk/batch/batch:1.0.0",
                    "supportedProviders": null
                }
            }
        },
        "invocation": [
            {
                "type": "word",
                "word": "batch"
            },
            {
                "type": "var",
                "variableNames": [
                    "var"
                ],
                "prefixGlobal": "",
                "suffixGlobal": "",
                "prefixVariable": "",
                "suffixVariable": "",
                "isPrefixVariablePartOfArg": false,
                "isSuffixVariablePartOfArg": false
            }
        ],
        "parameters": [
            {
                "type": "text",
                "name": "var",
                "optional": false,
                "defaultValue": null,
                "title": "",
                "description": "An example input variable"
            }
        ],
        "outputFileGlobs": [
            "*"
        ],
        "applicationType": "BATCH",
        "vnc": null,
        "web": null,
        "container": null,
        "environment": null,
        "allowAdditionalMounts": null,
        "allowAdditionalPeers": null,
        "allowMultiNode": false,
        "allowPublicIp": false,
        "allowPublicLink": null,
        "fileExtensions": [
        ],
        "licenseServers": [
        ]
    },
    "favorite": false,
    "tags": [
        "very-scientific"
    ]
}
*/

/* The user looks for a suitable machine */

const machineTypes = await callAPI(ProductsApi.browse(
    {
        "itemsPerPage": 50,
        "next": null,
        "consistency": null,
        "itemsToSkip": null,
        "filterName": null,
        "filterProvider": null,
        "filterArea": "COMPUTE",
        "filterCategory": null,
        "filterVersion": null,
        "showAllVersions": null,
        "includeBalance": null
    }
);

/*
machineTypes = {
    "itemsPerPage": 50,
    "items": [
        {
            "type": "compute",
            "balance": null,
            "name": "example-compute",
            "pricePerUnit": 1000000,
            "category": {
                "name": "example-compute",
                "provider": "example"
            },
            "description": "An example compute product",
            "priority": 0,
            "cpu": 10,
            "memoryInGigs": 20,
            "gpu": 0,
            "version": 1,
            "freeToUse": false,
            "unitOfPrice": "CREDITS_PER_MINUTE",
            "chargeType": "ABSOLUTE",
            "hiddenInGrantApplications": false,
            "productType": "COMPUTE"
        }
    ],
    "next": null
}
*/

/* The user starts the Job with input based on previous requests */

await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "a-batch-application",
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
                "parameters": {
                    "var": {
                        "type": "text",
                        "value": "Example"
                    }
                },
                "resources": null,
                "timeAllocation": null,
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
            "id": "48920"
        }
    ]
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

# The user finds an interesting application from the catalog

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/apps?itemsPerPage=50&page=0" 

# applications = 
# {
#     "itemsInTotal": 1,
#     "itemsPerPage": 50,
#     "pageNumber": 0,
#     "items": [
#         {
#             "metadata": {
#                 "name": "a-batch-application",
#                 "version": "1.0.0",
#                 "authors": [
#                     "UCloud"
#                 ],
#                 "title": "A Batch Application",
#                 "description": "This is a batch application",
#                 "website": null,
#                 "public": true
#             },
#             "favorite": false,
#             "tags": [
#                 "very-scientific"
#             ]
#         }
#     ]
# }

# The user selects the first application ('batch' in version '1.0.0')

# The user requests additional information about the application

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/apps/byNameAndVersion?appName=a-batch-application&appVersion=1.0.0" 

# application = 
# {
#     "metadata": {
#         "name": "a-batch-application",
#         "version": "1.0.0",
#         "authors": [
#             "UCloud"
#         ],
#         "title": "A Batch Application",
#         "description": "This is a batch application",
#         "website": null,
#         "public": true
#     },
#     "invocation": {
#         "tool": {
#             "name": "batch-tool",
#             "version": "1.0.0",
#             "tool": {
#                 "owner": "user",
#                 "createdAt": 1632979836013,
#                 "modifiedAt": 1632979836013,
#                 "description": {
#                     "info": {
#                         "name": "batch-tool",
#                         "version": "1.0.0"
#                     },
#                     "container": null,
#                     "defaultNumberOfNodes": 1,
#                     "defaultTimeAllocation": {
#                         "hours": 1,
#                         "minutes": 0,
#                         "seconds": 0
#                     },
#                     "requiredModules": [
#                     ],
#                     "authors": [
#                         "UCloud"
#                     ],
#                     "title": "Batch tool",
#                     "description": "Batch tool",
#                     "backend": "DOCKER",
#                     "license": "None",
#                     "image": "dreg.cloud.sdu.dk/batch/batch:1.0.0",
#                     "supportedProviders": null
#                 }
#             }
#         },
#         "invocation": [
#             {
#                 "type": "word",
#                 "word": "batch"
#             },
#             {
#                 "type": "var",
#                 "variableNames": [
#                     "var"
#                 ],
#                 "prefixGlobal": "",
#                 "suffixGlobal": "",
#                 "prefixVariable": "",
#                 "suffixVariable": "",
#                 "isPrefixVariablePartOfArg": false,
#                 "isSuffixVariablePartOfArg": false
#             }
#         ],
#         "parameters": [
#             {
#                 "type": "text",
#                 "name": "var",
#                 "optional": false,
#                 "defaultValue": null,
#                 "title": "",
#                 "description": "An example input variable"
#             }
#         ],
#         "outputFileGlobs": [
#             "*"
#         ],
#         "applicationType": "BATCH",
#         "vnc": null,
#         "web": null,
#         "container": null,
#         "environment": null,
#         "allowAdditionalMounts": null,
#         "allowAdditionalPeers": null,
#         "allowMultiNode": false,
#         "allowPublicIp": false,
#         "allowPublicLink": null,
#         "fileExtensions": [
#         ],
#         "licenseServers": [
#         ]
#     },
#     "favorite": false,
#     "tags": [
#         "very-scientific"
#     ]
# }

# The user looks for a suitable machine

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/products/browse?itemsPerPage=50&filterArea=COMPUTE" 

# machineTypes = 
# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "type": "compute",
#             "balance": null,
#             "name": "example-compute",
#             "pricePerUnit": 1000000,
#             "category": {
#                 "name": "example-compute",
#                 "provider": "example"
#             },
#             "description": "An example compute product",
#             "priority": 0,
#             "cpu": 10,
#             "memoryInGigs": 20,
#             "gpu": 0,
#             "version": 1,
#             "freeToUse": false,
#             "unitOfPrice": "CREDITS_PER_MINUTE",
#             "chargeType": "ABSOLUTE",
#             "hiddenInGrantApplications": false,
#             "productType": "COMPUTE"
#         }
#     ],
#     "next": null
# }

# The user starts the Job with input based on previous requests

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "a-batch-application",
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
            "parameters": {
                "var": {
                    "type": "text",
                    "value": "Example"
                }
            },
            "resources": null,
            "timeAllocation": null,
            "openedFile": null,
            "restartOnExit": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "48920"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_create.png)

</details>


