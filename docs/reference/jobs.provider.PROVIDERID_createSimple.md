[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# Example: Simple batch Job with life-cycle events

<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>You should understand Products, Applications and Jobs before reading this</li>
<li>The provider must support containerized Applications</li>
<li>The provider must implement the retrieveProducts call</li>
</ul></td></tr>
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

/* In this example we will show the creation of a simple batch Job. The procedure starts with the
Provider receives a create request from UCloud/Core */


/* The request below contains a lot of information. We recommend that you read about and understand
Products, Applications and Jobs before you continue. We will attempt to summarize the information
below:

- The request contains one or more Jobs. The Provider should schedule each of them on their
  infrastructure.
- The `id` of a Job is unique, globally, in UCloud.
- The `owner` references the UCloud identity and workspace of the creator
- The `specification` contains the user's request
- The `status` contains UCloud's view of the Job _AND_ resolved resources required for the Job

In this example:

- Exactly one Job will be created. 
  - `items` contains only one Job
  
- This Job will run a `BATCH` application
  - See `status.resolvedApplication.invocation.applicationType`
  
- It will run on the `example-compute-1` machine-type
  - See `specification.product` and `status.resolvedProduct`
  
- The application should launch the `acme/batch:1.0.0` container
  - `status.resolvedApplication.invocation.tool.tool.description.backend`
  - `status.resolvedApplication.invocation.tool.tool.description.image`
  
- It will be invoked with `acme-batch --debug "Hello, World!"`. 
  - The invocation is created from `status.resolvedApplication.invocation.invocation`
  - With parameters defined in `status.resolvedApplication.invocation.parameters`
  - And values defined in `specification.parameters`
  
- The Job should be scheduled with a max wall-time of 1 hour 
  - See `specification.timeAllocation`
  
- ...on exactly 1 node.
  - See `specification.replicas` */

JobsProvider.create.call(
    bulkRequestOf(Job(
        createdAt = 1633329776235, 
        id = "54112", 
        output = null, 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = null, 
        specification = JobSpecification(
            allowDuplicateJob = false, 
            application = NameAndVersion(
                name = "acme-batch", 
                version = "1.0.0", 
            ), 
            name = null, 
            openedFile = null, 
            parameters = mapOf("debug" to AppParameterValue.Bool(
                value = true, 
            ), "value" to AppParameterValue.Text(
                value = "Hello, World!", 
            )), 
            product = ProductReference(
                category = "example-compute", 
                id = "example-compute-1", 
                provider = "example", 
            ), 
            replicas = 1, 
            resources = null, 
            restartOnExit = null, 
            sshEnabled = null, 
            timeAllocation = SimpleDuration(
                hours = 1, 
                minutes = 0, 
                seconds = 0, 
            ), 
        ), 
        status = JobStatus(
            allowRestart = false, 
            expiresAt = null, 
            jobParametersJson = null, 
            resolvedApplication = Application(
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
                        word = "acme-batch", 
                    ), VariableInvocationParameter(
                        isPrefixVariablePartOfArg = false, 
                        isSuffixVariablePartOfArg = false, 
                        prefixGlobal = "--debug ", 
                        prefixVariable = "", 
                        suffixGlobal = "", 
                        suffixVariable = "", 
                        variableNames = listOf("debug"), 
                    ), VariableInvocationParameter(
                        isPrefixVariablePartOfArg = false, 
                        isSuffixVariablePartOfArg = false, 
                        prefixGlobal = "", 
                        prefixVariable = "", 
                        suffixGlobal = "", 
                        suffixVariable = "", 
                        variableNames = listOf("value"), 
                    )), 
                    licenseServers = emptyList(), 
                    modules = null, 
                    outputFileGlobs = listOf("*"), 
                    parameters = listOf(ApplicationParameter.Bool(
                        defaultValue = null, 
                        description = "Should debug be enabled?", 
                        falseValue = "false", 
                        name = "debug", 
                        optional = false, 
                        title = "", 
                        trueValue = "true", 
                    ), ApplicationParameter.Text(
                        defaultValue = null, 
                        description = "The value for the batch application", 
                        name = "value", 
                        optional = false, 
                        title = "", 
                    )), 
                    shouldAllowAdditionalMounts = false, 
                    shouldAllowAdditionalPeers = true, 
                    ssh = null, 
                    tool = ToolReference(
                        name = "acme-batch", 
                        tool = Tool(
                            createdAt = 1633329776235, 
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
                                description = "An example tool", 
                                image = "acme/batch:1.0.0", 
                                info = NameAndVersion(
                                    name = "acme-batch", 
                                    version = "1.0.0", 
                                ), 
                                license = "None", 
                                requiredModules = emptyList(), 
                                supportedProviders = null, 
                                title = "Acme batch", 
                            ), 
                            modifiedAt = 1633329776235, 
                            owner = "_ucloud", 
                        ), 
                        version = "1.0.0", 
                    ), 
                    vnc = null, 
                    web = null, 
                ), 
                metadata = ApplicationMetadata(
                    authors = listOf("UCloud"), 
                    description = "An example application", 
                    flavorName = null, 
                    group = ApplicationGroup(
                        defaultApplication = null, 
                        description = null, 
                        id = 0, 
                        tags = emptyList(), 
                        title = "Test Group", 
                    ), 
                    isPublic = true, 
                    name = "acme-batch", 
                    public = true, 
                    title = "Acme batch", 
                    version = "1.0.0", 
                    website = null, 
                ), 
            ), 
            resolvedProduct = Product.Compute(
                allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
                category = ProductCategoryId(
                    id = "example-compute", 
                    name = "example-compute", 
                    provider = "example", 
                ), 
                chargeType = ChargeType.ABSOLUTE, 
                cpu = 1, 
                cpuModel = null, 
                description = "An example machine", 
                freeToUse = false, 
                gpu = 0, 
                gpuModel = null, 
                hiddenInGrantApplications = false, 
                memoryInGigs = 2, 
                memoryModel = null, 
                name = "example-compute-1", 
                pricePerUnit = 1000000, 
                priority = 0, 
                productType = ProductType.COMPUTE, 
                unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
                version = 1, 
                balance = null, 
                id = "example-compute-1", 
                maxUsableBalance = null, 
            ), 
            resolvedSupport = ResolvedSupport(
                product = Product.Compute(
                    allowAllocationRequestsFrom = AllocationRequestsGroup.ALL, 
                    category = ProductCategoryId(
                        id = "example-compute", 
                        name = "example-compute", 
                        provider = "example", 
                    ), 
                    chargeType = ChargeType.ABSOLUTE, 
                    cpu = 1, 
                    cpuModel = null, 
                    description = "An example machine", 
                    freeToUse = false, 
                    gpu = 0, 
                    gpuModel = null, 
                    hiddenInGrantApplications = false, 
                    memoryInGigs = 2, 
                    memoryModel = null, 
                    name = "example-compute-1", 
                    pricePerUnit = 1000000, 
                    priority = 0, 
                    productType = ProductType.COMPUTE, 
                    unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
                    version = 1, 
                    balance = null, 
                    id = "example-compute-1", 
                    maxUsableBalance = null, 
                ), 
                support = ComputeSupport(
                    docker = ComputeSupport.Docker(
                        enabled = true, 
                        logs = null, 
                        peers = null, 
                        terminal = null, 
                        timeExtension = null, 
                        utilization = null, 
                        vnc = null, 
                        web = null, 
                    ), 
                    maintenance = null, 
                    native = ComputeSupport.Native(
                        enabled = null, 
                        logs = null, 
                        terminal = null, 
                        timeExtension = null, 
                        utilization = null, 
                        vnc = null, 
                        web = null, 
                    ), 
                    product = ProductReference(
                        category = "example-compute", 
                        id = "example-compute-1", 
                        provider = "example", 
                    ), 
                    virtualMachine = ComputeSupport.VirtualMachine(
                        enabled = null, 
                        logs = null, 
                        suspension = null, 
                        terminal = null, 
                        timeExtension = null, 
                        utilization = null, 
                        vnc = null, 
                    ), 
                ), 
            ), 
            startedAt = null, 
            state = JobState.IN_QUEUE, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "54112", 
    )),
    ucloud
).orThrow()

/*
BulkResponse(
    responses = listOf(null), 
)
*/

/* 📝 Note: The response in this case indicates that the Provider chose not to generate an internal ID
for this Job. If an ID was provided, then on subsequent requests the `providerGeneratedId` of this
Job would be set accordingly. This feature can help providers keep track of their internal state
without having to actively maintain a mapping. */


/* The Provider will use this information to schedule the Job on their infrastructure. Through
background processing, the Provider will keep track of this Job. The Provider notifies UCloud of
state changes as they occur. This happens through the outgoing Control API. */

JobsControl.update.call(
    bulkRequestOf(ResourceUpdateAndId(
        id = "54112", 
        update = JobUpdate(
            allowRestart = null, 
            expectedDifferentState = null, 
            expectedState = null, 
            newMounts = null, 
            newTimeAllocation = null, 
            outputFolder = null, 
            state = JobState.RUNNING, 
            status = "The job is now running!", 
            timestamp = 0, 
        ), 
    )),
    provider
).orThrow()

/*
Unit
*/

/* 📝 Note: The timestamp field will be filled out by UCloud/Core */


/* ~ Some time later ~ */

JobsControl.update.call(
    bulkRequestOf(ResourceUpdateAndId(
        id = "54112", 
        update = JobUpdate(
            allowRestart = null, 
            expectedDifferentState = null, 
            expectedState = null, 
            newMounts = null, 
            newTimeAllocation = null, 
            outputFolder = null, 
            state = JobState.SUCCESS, 
            status = "The job has finished processing!", 
            timestamp = 0, 
        ), 
    )),
    provider
).orThrow()

/*
Unit
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

# In this example we will show the creation of a simple batch Job. The procedure starts with the
# Provider receives a create request from UCloud/Core

# The request below contains a lot of information. We recommend that you read about and understand
# Products, Applications and Jobs before you continue. We will attempt to summarize the information
# below:
# 
# - The request contains one or more Jobs. The Provider should schedule each of them on their
#   infrastructure.
# - The `id` of a Job is unique, globally, in UCloud.
# - The `owner` references the UCloud identity and workspace of the creator
# - The `specification` contains the user's request
# - The `status` contains UCloud's view of the Job _AND_ resolved resources required for the Job
# 
# In this example:
# 
# - Exactly one Job will be created. 
#   - `items` contains only one Job
#   
# - This Job will run a `BATCH` application
#   - See `status.resolvedApplication.invocation.applicationType`
#   
# - It will run on the `example-compute-1` machine-type
#   - See `specification.product` and `status.resolvedProduct`
#   
# - The application should launch the `acme/batch:1.0.0` container
#   - `status.resolvedApplication.invocation.tool.tool.description.backend`
#   - `status.resolvedApplication.invocation.tool.tool.description.image`
#   
# - It will be invoked with `acme-batch --debug "Hello, World!"`. 
#   - The invocation is created from `status.resolvedApplication.invocation.invocation`
#   - With parameters defined in `status.resolvedApplication.invocation.parameters`
#   - And values defined in `specification.parameters`
#   
# - The Job should be scheduled with a max wall-time of 1 hour 
#   - See `specification.timeAllocation`
#   
# - ...on exactly 1 node.
#   - See `specification.replicas`

# Authenticated as ucloud
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/ucloud/PROVIDERID/jobs" -d '{
    "items": [
        {
            "id": "54112",
            "owner": {
                "createdBy": "user",
                "project": null
            },
            "updates": [
            ],
            "specification": {
                "application": {
                    "name": "acme-batch",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute-1",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": null,
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": {
                    "debug": {
                        "type": "boolean",
                        "value": true
                    },
                    "value": {
                        "type": "text",
                        "value": "Hello, World!"
                    }
                },
                "resources": null,
                "timeAllocation": {
                    "hours": 1,
                    "minutes": 0,
                    "seconds": 0
                },
                "openedFile": null,
                "restartOnExit": null,
                "sshEnabled": null
            },
            "status": {
                "state": "IN_QUEUE",
                "jobParametersJson": null,
                "startedAt": null,
                "expiresAt": null,
                "resolvedApplication": {
                    "metadata": {
                        "name": "acme-batch",
                        "version": "1.0.0",
                        "authors": [
                            "UCloud"
                        ],
                        "title": "Acme batch",
                        "description": "An example application",
                        "website": null,
                        "public": true,
                        "flavorName": null,
                        "group": {
                            "id": 0,
                            "title": "Test Group",
                            "description": null,
                            "defaultApplication": null,
                            "tags": [
                            ]
                        }
                    },
                    "invocation": {
                        "tool": {
                            "name": "acme-batch",
                            "version": "1.0.0",
                            "tool": {
                                "owner": "_ucloud",
                                "createdAt": 1633329776235,
                                "modifiedAt": 1633329776235,
                                "description": {
                                    "info": {
                                        "name": "acme-batch",
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
                                    "title": "Acme batch",
                                    "description": "An example tool",
                                    "backend": "DOCKER",
                                    "license": "None",
                                    "image": "acme/batch:1.0.0",
                                    "supportedProviders": null
                                }
                            }
                        },
                        "invocation": [
                            {
                                "type": "word",
                                "word": "acme-batch"
                            },
                            {
                                "type": "var",
                                "variableNames": [
                                    "debug"
                                ],
                                "prefixGlobal": "--debug ",
                                "suffixGlobal": "",
                                "prefixVariable": "",
                                "suffixVariable": "",
                                "isPrefixVariablePartOfArg": false,
                                "isSuffixVariablePartOfArg": false
                            },
                            {
                                "type": "var",
                                "variableNames": [
                                    "value"
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
                                "type": "boolean",
                                "name": "debug",
                                "optional": false,
                                "defaultValue": null,
                                "title": "",
                                "description": "Should debug be enabled?",
                                "trueValue": "true",
                                "falseValue": "false"
                            },
                            {
                                "type": "text",
                                "name": "value",
                                "optional": false,
                                "defaultValue": null,
                                "title": "",
                                "description": "The value for the batch application"
                            }
                        ],
                        "outputFileGlobs": [
                            "*"
                        ],
                        "applicationType": "BATCH",
                        "vnc": null,
                        "web": null,
                        "ssh": null,
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
                        ],
                        "modules": null
                    }
                },
                "resolvedSupport": {
                    "product": {
                        "balance": null,
                        "maxUsableBalance": null,
                        "name": "example-compute-1",
                        "pricePerUnit": 1000000,
                        "category": {
                            "name": "example-compute",
                            "provider": "example"
                        },
                        "description": "An example machine",
                        "priority": 0,
                        "cpu": 1,
                        "memoryInGigs": 2,
                        "gpu": 0,
                        "cpuModel": null,
                        "memoryModel": null,
                        "gpuModel": null,
                        "version": 1,
                        "freeToUse": false,
                        "allowAllocationRequestsFrom": "ALL",
                        "unitOfPrice": "CREDITS_PER_MINUTE",
                        "chargeType": "ABSOLUTE",
                        "hiddenInGrantApplications": false,
                        "productType": "COMPUTE"
                    },
                    "support": {
                        "product": {
                            "id": "example-compute-1",
                            "category": "example-compute",
                            "provider": "example"
                        },
                        "docker": {
                            "enabled": true,
                            "web": null,
                            "vnc": null,
                            "logs": null,
                            "terminal": null,
                            "peers": null,
                            "timeExtension": null,
                            "utilization": null
                        },
                        "virtualMachine": {
                            "enabled": null,
                            "logs": null,
                            "vnc": null,
                            "terminal": null,
                            "timeExtension": null,
                            "suspension": null,
                            "utilization": null
                        },
                        "native": {
                            "enabled": null,
                            "logs": null,
                            "vnc": null,
                            "terminal": null,
                            "timeExtension": null,
                            "utilization": null,
                            "web": null
                        },
                        "maintenance": null
                    }
                },
                "resolvedProduct": {
                    "balance": null,
                    "maxUsableBalance": null,
                    "name": "example-compute-1",
                    "pricePerUnit": 1000000,
                    "category": {
                        "name": "example-compute",
                        "provider": "example"
                    },
                    "description": "An example machine",
                    "priority": 0,
                    "cpu": 1,
                    "memoryInGigs": 2,
                    "gpu": 0,
                    "cpuModel": null,
                    "memoryModel": null,
                    "gpuModel": null,
                    "version": 1,
                    "freeToUse": false,
                    "allowAllocationRequestsFrom": "ALL",
                    "unitOfPrice": "CREDITS_PER_MINUTE",
                    "chargeType": "ABSOLUTE",
                    "hiddenInGrantApplications": false,
                    "productType": "COMPUTE"
                },
                "allowRestart": false
            },
            "createdAt": 1633329776235,
            "output": null,
            "permissions": null
        }
    ]
}'


# {
#     "responses": [
#         null
#     ]
# }

# 📝 Note: The response in this case indicates that the Provider chose not to generate an internal ID
# for this Job. If an ID was provided, then on subsequent requests the `providerGeneratedId` of this
# Job would be set accordingly. This feature can help providers keep track of their internal state
# without having to actively maintain a mapping.

# The Provider will use this information to schedule the Job on their infrastructure. Through
# background processing, the Provider will keep track of this Job. The Provider notifies UCloud of
# state changes as they occur. This happens through the outgoing Control API.

# Authenticated as provider
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/control/update" -d '{
    "items": [
        {
            "id": "54112",
            "update": {
                "state": "RUNNING",
                "outputFolder": null,
                "status": "The job is now running!",
                "expectedState": null,
                "expectedDifferentState": null,
                "newTimeAllocation": null,
                "allowRestart": null,
                "newMounts": null,
                "timestamp": 0
            }
        }
    ]
}'


# {
# }

# 📝 Note: The timestamp field will be filled out by UCloud/Core

# ~ Some time later ~

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/control/update" -d '{
    "items": [
        {
            "id": "54112",
            "update": {
                "state": "SUCCESS",
                "outputFolder": null,
                "status": "The job has finished processing!",
                "expectedState": null,
                "expectedDifferentState": null,
                "newTimeAllocation": null,
                "allowRestart": null,
                "newMounts": null,
                "timestamp": 0
            }
        }
    ]
}'


# {
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs.provider.PROVIDERID_createSimple.png)

</details>


