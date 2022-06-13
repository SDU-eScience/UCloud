<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/appstore/apps.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/ips.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / Jobs
# Jobs

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Jobs in UCloud are the core abstraction used to describe units of computation._

## Rationale

__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    

The compute system allows for a variety of computational workloads to run on UCloud. All compute jobs
in UCloud run an [application](/docs/developer-guide/orchestration/compute/appstore/apps.md) on one or more
['nodes'](/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md). The type of applications determine
what the job does:
 
- __Batch__ applications provide support for long running computational workloads (typically containerized)
- __Web__ applications provide support for applications which expose a graphical web-interface
- __VNC__ applications provide support for interactive remote desktop workloads
- __Virtual machine__ applications provide support for more advanced workloads which aren't easily
  containerized or require special privileges

Every [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is created from a [`specification`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobSpecification.md). The specification
contains [input parameters](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md), such as files
and application flags, and additional resources. Zero or more resources can be connected to an application,
and provide services such as:

- Networking between multiple [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s
- [Storage](/docs/developer-guide/orchestration/storage/files.md)
- [Public links](/docs/developer-guide/orchestration/compute/ingress.md)
- [Public IPs](/docs/developer-guide/orchestration/compute/ips.md)
- [Software licenses](/docs/developer-guide/orchestration/compute/license.md)

---

__📝 Provider Note:__ This is the API exposed to end-users. See the table below for other relevant APIs.

| End-User | Provider (Ingoing) | Control (Outgoing) |
|----------|--------------------|--------------------|
| [`Jobs`](/docs/developer-guide/orchestration/compute/jobs.md) | [`JobsProvider`](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md) | [`JobsControl`](/docs/developer-guide/orchestration/compute/providers/jobs/outgoing.md) |

---

## Table of Contents
<details>
<summary>
<a href='#example-creating-a-simple-batch-job'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-creating-a-simple-batch-job'>Creating a simple batch Job</a></td></tr>
<tr><td><a href='#example-following-the-progress-of-a-job'>Following the progress of a Job</a></td></tr>
<tr><td><a href='#example-starting-an-interactive-terminal-session'>Starting an interactive terminal session</a></td></tr>
<tr><td><a href='#example-connecting-two-jobs-together'>Connecting two Jobs together</a></td></tr>
<tr><td><a href='#example-starting-a-job-with-a-public-link-(ingress)'>Starting a Job with a public link (Ingress)</a></td></tr>
<tr><td><a href='#example-using-licensed-software'>Using licensed software</a></td></tr>
<tr><td><a href='#example-using-a-remote-desktop-application-(vnc)'>Using a remote desktop Application (VNC)</a></td></tr>
<tr><td><a href='#example-using-a-web-application'>Using a web Application</a></td></tr>
<tr><td><a href='#example-losing-access-to-resources'>Losing access to resources</a></td></tr>
<tr><td><a href='#example-running-out-of-compute-credits'>Running out of compute credits</a></td></tr>
<tr><td><a href='#example-extending-a-job-and-terminating-it-early'>Extending a Job and terminating it early</a></td></tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#remote-procedure-calls'>2. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#browse'><code>browse</code></a></td>
<td>Browses the catalog of all Jobs</td>
</tr>
<tr>
<td><a href='#follow'><code>follow</code></a></td>
<td>Follow the progress of a job</td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieves a single Job</td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for all accessible providers</td>
</tr>
<tr>
<td><a href='#retrieveutilization'><code>retrieveUtilization</code></a></td>
<td>Retrieve information about how busy the provider's cluster currently is</td>
</tr>
<tr>
<td><a href='#search'><code>search</code></a></td>
<td>Searches the catalog of available resources</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates one or more resources</td>
</tr>
<tr>
<td><a href='#extend'><code>extend</code></a></td>
<td>Extend the duration of one or more jobs</td>
</tr>
<tr>
<td><a href='#init'><code>init</code></a></td>
<td>Request (potential) initialization of resources</td>
</tr>
<tr>
<td><a href='#openinteractivesession'><code>openInteractiveSession</code></a></td>
<td>Opens an interactive session (e.g. terminal, web or VNC)</td>
</tr>
<tr>
<td><a href='#suspend'><code>suspend</code></a></td>
<td>Suspend a job</td>
</tr>
<tr>
<td><a href='#terminate'><code>terminate</code></a></td>
<td>Request job cancellation and destruction</td>
</tr>
<tr>
<td><a href='#unsuspend'><code>unsuspend</code></a></td>
<td>Unsuspends a job</td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the ACL attached to a resource</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>3. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#job'><code>Job</code></a></td>
<td>A `Job` in UCloud is the core abstraction used to describe a unit of computation.</td>
</tr>
<tr>
<td><a href='#jobspecification'><code>JobSpecification</code></a></td>
<td>A specification of a Job</td>
</tr>
<tr>
<td><a href='#jobstate'><code>JobState</code></a></td>
<td>A value describing the current state of a Job</td>
</tr>
<tr>
<td><a href='#interactivesessiontype'><code>InteractiveSessionType</code></a></td>
<td>A value describing a type of 'interactive' session</td>
</tr>
<tr>
<td><a href='#jobincludeflags'><code>JobIncludeFlags</code></a></td>
<td>Flags used to tweak read operations of Jobs</td>
</tr>
<tr>
<td><a href='#computesupport'><code>ComputeSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#computesupport.docker'><code>ComputeSupport.Docker</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#computesupport.virtualmachine'><code>ComputeSupport.VirtualMachine</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#cpuandmemory'><code>CpuAndMemory</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exportedparameters'><code>ExportedParameters</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exportedparameters.resources'><code>ExportedParameters.Resources</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ingress'><code>Ingress</code></a></td>
<td>An L7 ingress-point (HTTP)</td>
</tr>
<tr>
<td><a href='#ingressspecification'><code>IngressSpecification</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ingressstate'><code>IngressState</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ingressstatus'><code>IngressStatus</code></a></td>
<td>The status of an `Ingress`</td>
</tr>
<tr>
<td><a href='#ingresssupport'><code>IngressSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ingressupdate'><code>IngressUpdate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobbindkind'><code>JobBindKind</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobbinding'><code>JobBinding</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#joboutput'><code>JobOutput</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobstatus'><code>JobStatus</code></a></td>
<td>Describes the current state of the `Resource`</td>
</tr>
<tr>
<td><a href='#jobupdate'><code>JobUpdate</code></a></td>
<td>Describes an update to the `Resource`</td>
</tr>
<tr>
<td><a href='#jobslog'><code>JobsLog</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#opensession'><code>OpenSession</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#opensession.shell'><code>OpenSession.Shell</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#opensession.vnc'><code>OpenSession.Vnc</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#opensession.web'><code>OpenSession.Web</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#opensessionwithprovider'><code>OpenSessionWithProvider</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#queuestatus'><code>QueueStatus</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#exportedparametersrequest'><code>ExportedParametersRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobsextendrequestitem'><code>JobsExtendRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobsopeninteractivesessionrequestitem'><code>JobsOpenInteractiveSessionRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobsretrieveutilizationrequest'><code>JobsRetrieveUtilizationRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobsfollowresponse'><code>JobsFollowResponse</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#jobsretrieveutilizationresponse'><code>JobsRetrieveUtilizationResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Creating a simple batch Job
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


## Example: Following the progress of a Job
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A running Job, with ID 123</li>
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
Jobs.follow.subscribe(
    FindByStringId(
        id = "123", 
    ),
    user,
    handler = { /* will receive messages listed below */ }
)

/*
JobsFollowResponse(
    log = emptyList(), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.IN_QUEUE, 
    ), 
    updates = emptyList(), 
)
*/

/*
JobsFollowResponse(
    log = emptyList(), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
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
        state = JobState.RUNNING, 
        status = "The job is now running", 
        timestamp = 1633680152778, 
    )), 
)
*/

/*
JobsFollowResponse(
    log = listOf(JobsLog(
        rank = 0, 
        stderr = null, 
        stdout = "GNU bash, version 5.0.17(1)-release (x86_64-pc-linux-gnu)" + "\n" + 
            "Copyright (C) 2019 Free Software Foundation, Inc." + "\n" + 
            "License GPLv3+: GNU GPL version 3 or later <http://gnu.org/licenses/gpl.html>" + "\n" + 
            "" + "\n" + 
            "This is free software; you are free to change and redistribute it." + "\n" + 
            "There is NO WARRANTY, to the extent permitted by law.", 
    )), 
    newStatus = JobStatus(
        allowRestart = false, 
        expiresAt = null, 
        jobParametersJson = null, 
        resolvedApplication = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        startedAt = null, 
        state = JobState.RUNNING, 
    ), 
    updates = emptyList(), 
)
*/

/*
JobsFollowResponse(
    log = emptyList(), 
    newStatus = JobStatus(
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
        state = JobState.SUCCESS, 
        status = "The job is no longer running", 
        timestamp = 1633680152778, 
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

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_follow.png)

</details>


## Example: Starting an interactive terminal session
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated by clicking on 'Open Terminal' of a running Job</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>A running Job with ID 123</li>
<li>The provider must support the terminal functionality</li>
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
Jobs.retrieveProducts.call(
    Unit,
    user
).orThrow()

/*
SupportByProvider(
    productsByProvider = mapOf("example" to listOf(ResolvedSupport(
        product = Product.Compute(
            category = ProductCategoryId(
                id = "compute-example", 
                name = "compute-example", 
                provider = "example", 
            ), 
            chargeType = ChargeType.ABSOLUTE, 
            cpu = 1, 
            description = "An example machine", 
            freeToUse = false, 
            gpu = 0, 
            hiddenInGrantApplications = false, 
            memoryInGigs = 2, 
            name = "compute-example", 
            pricePerUnit = 1000000, 
            priority = 0, 
            productType = ProductType.COMPUTE, 
            unitOfPrice = ProductPriceUnit.CREDITS_PER_MINUTE, 
            version = 1, 
            balance = null, 
            id = "compute-example", 
        ), 
        support = ComputeSupport(
            docker = ComputeSupport.Docker(
                enabled = true, 
                logs = null, 
                peers = null, 
                terminal = true, 
                timeExtension = null, 
                utilization = null, 
                vnc = null, 
                web = null, 
            ), 
            product = ProductReference(
                category = "compute-example", 
                id = "compute-example", 
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
    ))), 
)
*/

/* 📝 Note: The machine has support for the 'terminal' feature */

Jobs.openInteractiveSession.call(
    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(
        id = "123", 
        rank = 1, 
        sessionType = InteractiveSessionType.SHELL, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(OpenSessionWithProvider(
        providerDomain = "provider.example.com", 
        providerId = "example", 
        session = OpenSession.Shell(
            jobId = "123", 
            rank = 1, 
            sessionIdentifier = "a81ea644-58f5-44d9-8e94-89f81666c441", 
        ), 
    )), 
)
*/

/* The session is now open and we can establish a shell connection directly with provider.example.com */

Shells.open.subscribe(
    ShellRequest.Initialize(
        cols = 80, 
        rows = 24, 
        sessionIdentifier = "a81ea644-58f5-44d9-8e94-89f81666c441", 
    ),
    user,
    handler = { /* will receive messages listed below */ }
)

/*
ShellResponse.Data(
    data = "user@machine:~$ ", 
)
*/

Shells.open.call(
    ShellRequest.Input(
        data = "ls -1" + "\n" + 
            "", 
    ),
    user
).orThrow()

/*
ShellResponse.Data(
    data = "ls -1" + "\n" + 
        "", 
)
*/

/*
ShellResponse.Data(
    data = "hello_world.txt" + "\n" + 
        "", 
)
*/

/*
ShellResponse.Data(
    data = "user@machine:~$ ", 
)
*/

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript
// Authenticated as user
await callAPI(JobsApi.retrieveProducts(
    {
    }
);

/*
{
    "productsByProvider": {
        "example": [
            {
                "product": {
                    "balance": null,
                    "name": "compute-example",
                    "pricePerUnit": 1000000,
                    "category": {
                        "name": "compute-example",
                        "provider": "example"
                    },
                    "description": "An example machine",
                    "priority": 0,
                    "cpu": 1,
                    "memoryInGigs": 2,
                    "gpu": 0,
                    "version": 1,
                    "freeToUse": false,
                    "unitOfPrice": "CREDITS_PER_MINUTE",
                    "chargeType": "ABSOLUTE",
                    "hiddenInGrantApplications": false,
                    "productType": "COMPUTE"
                },
                "support": {
                    "product": {
                        "id": "compute-example",
                        "category": "compute-example",
                        "provider": "example"
                    },
                    "docker": {
                        "enabled": true,
                        "web": null,
                        "vnc": null,
                        "logs": null,
                        "terminal": true,
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
                    }
                }
            }
        ]
    }
}
*/

/* 📝 Note: The machine has support for the 'terminal' feature */

await callAPI(JobsApi.openInteractiveSession(
    {
        "items": [
            {
                "id": "123",
                "rank": 1,
                "sessionType": "SHELL"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "providerDomain": "provider.example.com",
            "providerId": "example",
            "session": {
                "type": "shell",
                "jobId": "123",
                "rank": 1,
                "sessionIdentifier": "a81ea644-58f5-44d9-8e94-89f81666c441"
            }
        }
    ]
}
*/

/* The session is now open and we can establish a shell connection directly with provider.example.com */

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

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/jobs/retrieveProducts" 

# {
#     "productsByProvider": {
#         "example": [
#             {
#                 "product": {
#                     "balance": null,
#                     "name": "compute-example",
#                     "pricePerUnit": 1000000,
#                     "category": {
#                         "name": "compute-example",
#                         "provider": "example"
#                     },
#                     "description": "An example machine",
#                     "priority": 0,
#                     "cpu": 1,
#                     "memoryInGigs": 2,
#                     "gpu": 0,
#                     "version": 1,
#                     "freeToUse": false,
#                     "unitOfPrice": "CREDITS_PER_MINUTE",
#                     "chargeType": "ABSOLUTE",
#                     "hiddenInGrantApplications": false,
#                     "productType": "COMPUTE"
#                 },
#                 "support": {
#                     "product": {
#                         "id": "compute-example",
#                         "category": "compute-example",
#                         "provider": "example"
#                     },
#                     "docker": {
#                         "enabled": true,
#                         "web": null,
#                         "vnc": null,
#                         "logs": null,
#                         "terminal": true,
#                         "peers": null,
#                         "timeExtension": null,
#                         "utilization": null
#                     },
#                     "virtualMachine": {
#                         "enabled": null,
#                         "logs": null,
#                         "vnc": null,
#                         "terminal": null,
#                         "timeExtension": null,
#                         "suspension": null,
#                         "utilization": null
#                     }
#                 }
#             }
#         ]
#     }
# }

# 📝 Note: The machine has support for the 'terminal' feature

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/interactiveSession" -d '{
    "items": [
        {
            "id": "123",
            "rank": 1,
            "sessionType": "SHELL"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "providerDomain": "provider.example.com",
#             "providerId": "example",
#             "session": {
#                 "type": "shell",
#                 "jobId": "123",
#                 "rank": 1,
#                 "sessionIdentifier": "a81ea644-58f5-44d9-8e94-89f81666c441"
#             }
#         }
#     ]
# }

# The session is now open and we can establish a shell connection directly with provider.example.com

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_terminal.png)

</details>


## Example: Connecting two Jobs together
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Trigger</th><td>User initiated</td></tr>
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

/* In this example our user wish to deploy a simple web application which connects to a database server */


/* The user first provision a database server using an Application */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-database", 
            version = "1.0.0", 
        ), 
        name = "my-database", 
        openedFile = null, 
        parameters = mapOf("dataStore" to AppParameterValue.File(
            path = "/123/acme-database", 
            readOnly = false, 
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
        id = "4101", 
    )), 
)
*/

/* The database is now `RUNNING` with the persistent from `/123/acme-database` */


/* By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
can be updated by connecting one or more Jobs together. We will now do this using the Application.
"Peer" feature. This feature is commonly referred to as "Connect to Job". */


/* We will now start our web-application and connect it to our existing database Job */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-web-app", 
            version = "1.0.0", 
        ), 
        name = "my-web-app", 
        openedFile = null, 
        parameters = null, 
        product = ProductReference(
            category = "example-compute", 
            id = "example-compute", 
            provider = "example", 
        ), 
        replicas = 1, 
        resources = listOf(AppParameterValue.Peer(
            hostname = "database", 
            jobId = "4101", 
        )), 
        restartOnExit = null, 
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "4150", 
    )), 
)
*/

/* The web-application can now connect to the database using the 'database' hostname, as specified in
the JobSpecification. */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example our user wish to deploy a simple web application which connects to a database server */


/* The user first provision a database server using an Application */

// Authenticated as user
await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-database",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": "my-database",
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": {
                    "dataStore": {
                        "type": "file",
                        "path": "/123/acme-database",
                        "readOnly": false
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
            "id": "4101"
        }
    ]
}
*/

/* The database is now `RUNNING` with the persistent from `/123/acme-database` */


/* By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
can be updated by connecting one or more Jobs together. We will now do this using the Application.
"Peer" feature. This feature is commonly referred to as "Connect to Job". */


/* We will now start our web-application and connect it to our existing database Job */

await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-web-app",
                    "version": "1.0.0"
                },
                "product": {
                    "id": "example-compute",
                    "category": "example-compute",
                    "provider": "example"
                },
                "name": "my-web-app",
                "replicas": 1,
                "allowDuplicateJob": false,
                "parameters": null,
                "resources": [
                    {
                        "type": "peer",
                        "hostname": "database",
                        "jobId": "4101"
                    }
                ],
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
            "id": "4150"
        }
    ]
}
*/

/* The web-application can now connect to the database using the 'database' hostname, as specified in
the JobSpecification. */

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

# In this example our user wish to deploy a simple web application which connects to a database server

# The user first provision a database server using an Application

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-database",
                "version": "1.0.0"
            },
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            },
            "name": "my-database",
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": {
                "dataStore": {
                    "type": "file",
                    "path": "/123/acme-database",
                    "readOnly": false
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
#             "id": "4101"
#         }
#     ]
# }

# The database is now `RUNNING` with the persistent from `/123/acme-database`

# By default, the UCloud firewall will not allow any ingoing connections to the Job. This firewall
# can be updated by connecting one or more Jobs together. We will now do this using the Application.
# "Peer" feature. This feature is commonly referred to as "Connect to Job".

# We will now start our web-application and connect it to our existing database Job

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-web-app",
                "version": "1.0.0"
            },
            "product": {
                "id": "example-compute",
                "category": "example-compute",
                "provider": "example"
            },
            "name": "my-web-app",
            "replicas": 1,
            "allowDuplicateJob": false,
            "parameters": null,
            "resources": [
                {
                    "type": "peer",
                    "hostname": "database",
                    "jobId": "4101"
                }
            ],
            "timeAllocation": null,
            "openedFile": null,
            "restartOnExit": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "4150"
#         }
#     ]
# }

# The web-application can now connect to the database using the 'database' hostname, as specified in
# the JobSpecification.

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_peers.png)

</details>


## Example: Starting a Job with a public link (Ingress)
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
                "restartOnExit": null
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
            "restartOnExit": null
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


## Example: Using licensed software
<table>
<tr><th>Frequency of use</th><td>Common</td></tr>
<tr><th>Pre-conditions</th><td><ul>
<li>User has already been granted credits for the license (typically through Grants)</li>
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

/* In this example, the user will run a piece of licensed software. */


/* First, the user must activate a copy of their license, which has previously been granted to them through the Grant system. */

Licenses.create.call(
    bulkRequestOf(LicenseSpecification(
        product = ProductReference(
            category = "example-license", 
            id = "example-license", 
            provider = "example", 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "56231", 
    )), 
)
*/

/* This license can now freely be used in Jobs */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-licensed-software", 
            version = "1.0.0", 
        ), 
        name = null, 
        openedFile = null, 
        parameters = mapOf("license" to AppParameterValue.License(
            id = "56231", 
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
        id = "55123", 
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

/* In this example, the user will run a piece of licensed software. */


/* First, the user must activate a copy of their license, which has previously been granted to them through the Grant system. */

// Authenticated as user
await callAPI(LicensesApi.create(
    {
        "items": [
            {
                "product": {
                    "id": "example-license",
                    "category": "example-license",
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
            "id": "56231"
        }
    ]
}
*/

/* This license can now freely be used in Jobs */

await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-licensed-software",
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
                    "license": {
                        "type": "license_server",
                        "id": "56231"
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
            "id": "55123"
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

# In this example, the user will run a piece of licensed software.

# First, the user must activate a copy of their license, which has previously been granted to them through the Grant system.

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/licenses" -d '{
    "items": [
        {
            "product": {
                "id": "example-license",
                "category": "example-license",
                "provider": "example"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "56231"
#         }
#     ]
# }

# This license can now freely be used in Jobs

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-licensed-software",
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
                "license": {
                    "type": "license_server",
                    "id": "56231"
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
#             "id": "55123"
#         }
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_license.png)

</details>


## Example: Using a remote desktop Application (VNC)
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

/* In this example, the user will create a Job which uses an Application that exposes a VNC interface */

Jobs.create.call(
    bulkRequestOf(JobSpecification(
        allowDuplicateJob = false, 
        application = NameAndVersion(
            name = "acme-remote-desktop", 
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
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "51231", 
    )), 
)
*/
Jobs.openInteractiveSession.call(
    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(
        id = "51231", 
        rank = 0, 
        sessionType = InteractiveSessionType.VNC, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(OpenSessionWithProvider(
        providerDomain = "provider.example.com", 
        providerId = "example", 
        session = OpenSession.Vnc(
            jobId = "51231", 
            password = "e7ccc6e0870250073286c44545e6b41820d1db7f", 
            rank = 0, 
            url = "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com", 
        ), 
    )), 
)
*/

/* The user can now connect to the remote desktop using the VNC protocol with the above details */


/* NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
established directly from the browser.

You can read more about the protocol here: https://novnc.com */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, the user will create a Job which uses an Application that exposes a VNC interface */

// Authenticated as user
await callAPI(JobsApi.create(
    {
        "items": [
            {
                "application": {
                    "name": "acme-remote-desktop",
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
                "restartOnExit": null
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "id": "51231"
        }
    ]
}
*/
await callAPI(JobsApi.openInteractiveSession(
    {
        "items": [
            {
                "id": "51231",
                "rank": 0,
                "sessionType": "VNC"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "providerDomain": "provider.example.com",
            "providerId": "example",
            "session": {
                "type": "vnc",
                "jobId": "51231",
                "rank": 0,
                "url": "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com",
                "password": "e7ccc6e0870250073286c44545e6b41820d1db7f"
            }
        }
    ]
}
*/

/* The user can now connect to the remote desktop using the VNC protocol with the above details */


/* NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
established directly from the browser.

You can read more about the protocol here: https://novnc.com */

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

# In this example, the user will create a Job which uses an Application that exposes a VNC interface

# Authenticated as user
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs" -d '{
    "items": [
        {
            "application": {
                "name": "acme-remote-desktop",
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
            "restartOnExit": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "51231"
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/interactiveSession" -d '{
    "items": [
        {
            "id": "51231",
            "rank": 0,
            "sessionType": "VNC"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "providerDomain": "provider.example.com",
#             "providerId": "example",
#             "session": {
#                 "type": "vnc",
#                 "jobId": "51231",
#                 "rank": 0,
#                 "url": "vnc-69521c85-4811-43e6-9de3-2a48614d04ab.provider.example.com",
#                 "password": "e7ccc6e0870250073286c44545e6b41820d1db7f"
#             }
#         }
#     ]
# }

# The user can now connect to the remote desktop using the VNC protocol with the above details

# NOTE: UCloud expects this to support the VNC over WebSockets, as it allows for a connection to be
# established directly from the browser.
# 
# You can read more about the protocol here: https://novnc.com

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_vnc.png)

</details>


## Example: Using a web Application
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

/* In this example, the user will create a Job which uses an Application that exposes a web interface */

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
        timeAllocation = null, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "62342", 
    )), 
)
*/
Jobs.openInteractiveSession.call(
    bulkRequestOf(JobsOpenInteractiveSessionRequestItem(
        id = "62342", 
        rank = 0, 
        sessionType = InteractiveSessionType.WEB, 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(OpenSessionWithProvider(
        providerDomain = "provider.example.com", 
        providerId = "example", 
        session = OpenSession.Web(
            jobId = "62342", 
            rank = 0, 
            redirectClientTo = "app-gateway.provider.example.com?token=aa2dd29a-fe83-4201-b28e-fe211f94ac9d", 
        ), 
    )), 
)
*/

/* The user should now proceed to the link provided in the response */

```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, the user will create a Job which uses an Application that exposes a web interface */

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
            "id": "62342"
        }
    ]
}
*/
await callAPI(JobsApi.openInteractiveSession(
    {
        "items": [
            {
                "id": "62342",
                "rank": 0,
                "sessionType": "WEB"
            }
        ]
    }
);

/*
{
    "responses": [
        {
            "providerDomain": "provider.example.com",
            "providerId": "example",
            "session": {
                "type": "web",
                "jobId": "62342",
                "rank": 0,
                "redirectClientTo": "app-gateway.provider.example.com?token=aa2dd29a-fe83-4201-b28e-fe211f94ac9d"
            }
        }
    ]
}
*/

/* The user should now proceed to the link provided in the response */

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

# In this example, the user will create a Job which uses an Application that exposes a web interface

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
            "timeAllocation": null,
            "openedFile": null,
            "restartOnExit": null
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "62342"
#         }
#     ]
# }

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/jobs/interactiveSession" -d '{
    "items": [
        {
            "id": "62342",
            "rank": 0,
            "sessionType": "WEB"
        }
    ]
}'


# {
#     "responses": [
#         {
#             "providerDomain": "provider.example.com",
#             "providerId": "example",
#             "session": {
#                 "type": "web",
#                 "jobId": "62342",
#                 "rank": 0,
#                 "redirectClientTo": "app-gateway.provider.example.com?token=aa2dd29a-fe83-4201-b28e-fe211f94ac9d"
#             }
#         }
#     ]
# }

# The user should now proceed to the link provided in the response

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/jobs_web.png)

</details>


## Example: Losing access to resources
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
        restartOnExit = null, 
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
        restartOnExit = null, 
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
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633588976235
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
            "timestamp": 1633588981235
        },
        {
            "state": "SUCCESS",
            "outputFolder": null,
            "status": "Your job has been terminated (Lost permissions)",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "allowRestart": null,
            "newMounts": null,
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
#             "status": "Your job has been terminated (Lost permissions)",
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
#         "resources": [
#             {
#                 "type": "file",
#                 "path": "/12512/shared",
#                 "readOnly": false
#             }
#         ],
#         "timeAllocation": null,
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


## Example: Running out of compute credits
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
        restartOnExit = null, 
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
            "allowRestart": null,
            "newMounts": null,
            "timestamp": 1633588976235
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
            "timestamp": 1633588981235
        },
        {
            "state": "SUCCESS",
            "outputFolder": null,
            "status": "Your job has been terminated (No more credits)",
            "expectedState": null,
            "expectedDifferentState": null,
            "newTimeAllocation": null,
            "allowRestart": null,
            "newMounts": null,
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


## Example: Extending a Job and terminating it early
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



## Remote Procedure Calls

### `browse`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of all Jobs_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#job'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The catalog of all [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s works through the normal pagination and the return value can be
adjusted through the [flags](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags.md). This can include filtering by a specific
application or looking at [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s of a specific state, such as
(`RUNNING`)[/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobState.md).


### `follow`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Follow the progress of a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#jobsfollowresponse'>JobsFollowResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Opens a WebSocket subscription to receive updates about a job. These updates include:

- Messages from the provider. For example an update describing state changes or future maintenance.
- State changes from UCloud. For example transition from [`IN_QUEUE`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobState.md) to
  [`RUNNING`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobState.md).
- If supported by the provider, `stdout` and `stderr` from the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)


### `retrieve`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a single Job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='#job'>Job</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md'>Product.Compute</a>, <a href='#computesupport'>ComputeSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `retrieveUtilization`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve information about how busy the provider's cluster currently is_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#jobsretrieveutilizationrequest'>JobsRetrieveUtilizationRequest</a></code>|<code><a href='#jobsretrieveutilizationresponse'>JobsRetrieveUtilizationResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will return information about how busy a cluster is. This endpoint is only used for
informational purposes. UCloud does not use this information for any accounting purposes.


### `search`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceSearchRequest.md'>ResourceSearchRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#job'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobspecification'>JobSpecification</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `extend`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Extend the duration of one or more jobs_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobsextendrequestitem'>JobsExtendRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This will extend the duration of one or more jobs in a bulk request. Extension of a job will add to
the current deadline of a job. Note that not all providers support this features. Providers which
do not support it will have it listed in their manifest. If a provider is asked to extend a deadline
when not supported it will send back a 400 bad request.

This call makes no guarantee that all jobs are extended in a single transaction. If the provider
supports it, then all requests made against a single provider should be made in a single transaction.
Clients can determine if their extension request against a specific target was successful by checking
if the time remaining of the job has been updated.

This call will return 2XX if all jobs have successfully been extended. The job will fail with a
status code from the provider one the first extension which fails. UCloud will not attempt to extend
more jobs after the first failure.


### `init`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request (potential) initialization of resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request is sent by the client, if the client believes that initialization of resources 
might be needed. NOTE: This request might be sent even if initialization has already taken 
place. UCloud/Core does not check if initialization has already taken place, it simply validates
the request.


### `openInteractiveSession`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Opens an interactive session (e.g. terminal, web or VNC)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobsopeninteractivesessionrequestitem'>JobsOpenInteractiveSessionRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#opensessionwithprovider'>OpenSessionWithProvider</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `suspend`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Suspend a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Suspends the job, putting it in a paused state. Not all compute backends support this operation.
For compute backends which deals with Virtual Machines this will shutdown the Virtual Machine
without deleting any data.


### `terminate`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request job cancellation and destruction_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This call will request the cancellation of the associated jobs. This will make sure that the jobs
are eventually stopped and resources are released. If the job is running a virtual machine, then the
virtual machine will be stopped and destroyed. Persistent storage attached to the job will not be
deleted only temporary data from the job will be deleted.

This call is asynchronous and the cancellation may not be immediately visible in the job. Progress can
be followed using the [`jobs.retrieve`](/docs/reference/jobs.retrieve.md), [`jobs.browse`](/docs/reference/jobs.browse.md), [`jobs.follow`](/docs/reference/jobs.follow.md)  calls.


### `unsuspend`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Unsuspends a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Reverses the effects of suspending a job. The job is expected to return back to an `IN_QUEUE` or
`RUNNING` state.


### `updateAcl`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Job`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Job` in UCloud is the core abstraction used to describe a unit of computation._

```kotlin
data class Job(
    val id: String,
    val owner: ResourceOwner,
    val updates: List<JobUpdate>,
    val specification: JobSpecification,
    val status: JobStatus,
    val createdAt: Long,
    val output: JobOutput?,
    val permissions: ResourcePermissions?,
    val providerGeneratedId: String?,
)
```
They provide users a way to run their computations through a workflow similar to their own workstations but scaling to
much bigger and more machines. In a simplified view, a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  describes the following information:

- The `Application` which the provider should/is/has run (see [app-store](/docs/developer-guide/orchestration/compute/appstore/apps.md))
- The [input parameters](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md) required by a `Job`
- A reference to the appropriate [compute infrastructure](/docs/reference/dk.sdu.cloud.accounting.api.Product.md), this
  includes a reference to the _provider_

A `Job` is started by a user request containing the `specification` of a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job..md)  This information is verified by the UCloud
orchestrator and passed to the provider referenced by the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  itself. Assuming that the provider accepts this
information, the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is placed in its initial state, `IN_QUEUE`. You can read more about the requirements of the
compute environment and how to launch the software
correctly [here](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md).

At this point, the provider has acted on this information by placing the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  in its own equivalent of
a job queue. Once the provider realizes that the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is running, it will contact UCloud and place the 
[`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  in the `RUNNING` state. This indicates to UCloud that log files can be retrieved and that interactive
interfaces (`VNC`/`WEB`) are available.

Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  has
terminated successfully if no internal error occurred in UCloud and in the provider. This means that a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  whose
software returns with a non-zero exit code is still considered successful. A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  might, for example, be placed
in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
state. Any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  which is in a terminal state can no longer receive any updates or change its state.

At any point after the user submits the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), they may request cancellation of the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job..md)  This will
stop the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), delete any
[ephemeral resources](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md#ephemeral-resources) and release
any [bound resources](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md#resources).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Unique identifier for this job.
</summary>



UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> A reference to the owner of this job
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobupdate'>JobUpdate</a>&gt;</code></code> A list of status updates from the compute backend.
</summary>



The status updates tell a story of what happened with the job. This list is ordered by the timestamp in ascending order. The current state of the job will always be the last element. `updates` is guaranteed to always contain at least one element.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#jobspecification'>JobSpecification</a></code></code> The specification used to launch this job.
</summary>



This property is always available but must be explicitly requested.


</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#jobstatus'>JobStatus</a></code></code> A summary of the `Job`'s current status
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>output</code>: <code><code><a href='#joboutput'>JobOutput</a>?</code></code> Information regarding the output of this job.
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `JobSpecification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A specification of a Job_

```kotlin
data class JobSpecification(
    val application: NameAndVersion,
    val product: ProductReference,
    val name: String?,
    val replicas: Int?,
    val allowDuplicateJob: Boolean?,
    val parameters: JsonObject?,
    val resources: List<AppParameterValue>?,
    val timeAllocation: SimpleDuration?,
    val openedFile: String?,
    val restartOnExit: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>application</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.NameAndVersion.md'>NameAndVersion</a></code></code> A reference to the application which this job should execute
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> A reference to the product that this job will be executed on
</summary>





</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A name for this job assigned by the user.
</summary>



The name can help a user identify why and with which parameters a job was started. This value is suitable for display in user interfaces.


</details>

<details>
<summary>
<code>replicas</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> The number of replicas to start this job in
</summary>



The `resources` supplied will be mounted in every replica. Some `resources` might only be supported in an 'exclusive use' mode. This will cause the job to fail if `replicas != 1`.


</details>

<details>
<summary>
<code>allowDuplicateJob</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Allows the job to be started even when a job is running in an identical configuration
</summary>



By default, UCloud will prevent you from accidentally starting two jobs with identical configuration. This field must be set to `true` to allow you to create two jobs with identical configuration.


</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code> Parameters which are consumed by the job
</summary>



The available parameters are defined by the `application`. This attribute is not included by default unless `includeParameters` is specified.


</details>

<details>
<summary>
<code>resources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md'>AppParameterValue</a>&gt;?</code></code> Additional resources which are made available into the job
</summary>



This attribute is not included by default unless `includeParameters` is specified. Note: Not all resources can be attached to a job. UCloud supports the following parameter types as resources:

 - `file`
 - `peer`
 - `network`
 - `block_storage`
 - `ingress`


</details>

<details>
<summary>
<code>timeAllocation</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a>?</code></code> Time allocation for the job
</summary>



This value can be `null` which signifies that the job should not (automatically) expire. Note that some providers do not support `null`. When this value is not `null` it means that the job will be terminated, regardless of result, after the duration has expired. Some providers support extended this duration via the `extend` operation.


</details>

<details>
<summary>
<code>openedFile</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An optional path to the file which the user selected with the "Open with..." feature.
</summary>

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


This value is null if the application is not launched using the "Open with..." feature. The value of this
is passed to the compute environment in a provider specific way. We encourage providers to expose this as
an environment variable named `UCLOUD_OPEN_WITH_FILE` containing the absolute path of the file (in the
current environment). Remember that this path is the _UCloud_ path to the file and not the provider's path.


</details>

<details>
<summary>
<code>restartOnExit</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> A flag which indicates if this job should be restarted on exit.
</summary>

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


Not all providers support this feature and the Job will be rejected if not supported. This information can
also be queried through the product support feature.

If this flag is `true` then the Job will automatically be restarted when the provider notifies the
orchestrator about process termination. It is the responsibility of the orchestrator to notify the provider
about restarts. If the restarts are triggered by the provider, then the provider must not notify the
orchestrator about the termination. The orchestrator will trigger a new `create` request in a timely manner.
The orchestrator decides when to trigger a new `create`. For example, if a process is terminating often,
then the orchestrator might decide to wait before issuing a new `create`.


</details>



</details>



---

### `JobState`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A value describing the current state of a Job_

```kotlin
enum class JobState {
    IN_QUEUE,
    RUNNING,
    CANCELING,
    SUCCESS,
    FAILURE,
    EXPIRED,
    SUSPENDED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>IN_QUEUE</code> Any Job which is not yet ready
</summary>



More specifically, this state should apply to any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  for which all of the following holds:

- The [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  has been created
- It has never been in a final state
- The number of `replicas` which are running is less than the requested amount


</details>

<details>
<summary>
<code>RUNNING</code> A Job where all the tasks are running
</summary>



More specifically, this state should apply to any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  for which all of the following holds:

- All `replicas` of the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  have been started

---

__📝 NOTE:__ A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  can be `RUNNING` without actually being ready. For example, if a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  
exposes a web interface, then the web-interface doesn't have to be available yet. That is, the server might
still be running its initialization code.

---


</details>

<details>
<summary>
<code>CANCELING</code> A Job which has been cancelled but has not yet terminated
</summary>



---

__📝 NOTE:__ This is only a temporary state. The [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is expected to eventually transition to a final
state, typically the `SUCCESS` state.

---


</details>

<details>
<summary>
<code>SUCCESS</code> A Job which has terminated without a _scheduler_ error
</summary>



---

__📝 NOTE:__ A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  will complete successfully even if the user application exits with an unsuccessful 
status code.

---


</details>

<details>
<summary>
<code>FAILURE</code> A Job which has terminated with a failure
</summary>



---

__📝 NOTE:__ A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  should _only_ fail if it is the scheduler's fault

---


</details>

<details>
<summary>
<code>EXPIRED</code> A Job which has expired and was terminated as a result
</summary>



This state should only be used if the [`timeAllocation`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobSpecification.md) has expired. Any other
form of cancellation/termination should result in either `SUCCESS` or `FAILURE`.


</details>

<details>
<summary>
<code>SUSPENDED</code> A Job which might have previously run but is no longer running, this state is not final.
</summary>



Unlike SUCCESS and FAILURE a Job can transition from this state to one of the active states again.


</details>



</details>



---

### `InteractiveSessionType`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A value describing a type of 'interactive' session_

```kotlin
enum class InteractiveSessionType {
    WEB,
    VNC,
    SHELL,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>WEB</code>
</summary>





</details>

<details>
<summary>
<code>VNC</code>
</summary>





</details>

<details>
<summary>
<code>SHELL</code>
</summary>





</details>



</details>



---

### `JobIncludeFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Flags used to tweak read operations of Jobs_

```kotlin
data class JobIncludeFlags(
    val filterApplication: String?,
    val filterState: JobState?,
    val includeParameters: Boolean?,
    val includeApplication: Boolean?,
    val includeProduct: Boolean?,
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterIds: String?,
    val hideProductId: String?,
    val hideProductCategory: String?,
    val hideProvider: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>filterApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterState</code>: <code><code><a href='#jobstate'>JobState</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeParameters</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.parameters` and `specification.resources`
</summary>





</details>

<details>
<summary>
<code>includeApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedApplication`
</summary>





</details>

<details>
<summary>
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
</summary>





</details>

<details>
<summary>
<code>includeOthers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeUpdates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>includeSupport</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedAfter</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterCreatedBefore</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>filterProviderIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the provider ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>filterIds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters by the resource ID. The value is comma-separated.
</summary>





</details>

<details>
<summary>
<code>hideProductId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hideProductCategory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>hideProvider</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ComputeSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ComputeSupport(
    val product: ProductReference,
    val docker: ComputeSupport.Docker?,
    val virtualMachine: ComputeSupport.VirtualMachine?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>docker</code>: <code><code><a href='#computesupport.docker'>ComputeSupport.Docker</a>?</code></code> Support for `Tool`s using the `DOCKER` backend
</summary>





</details>

<details>
<summary>
<code>virtualMachine</code>: <code><code><a href='#computesupport.virtualmachine'>ComputeSupport.VirtualMachine</a>?</code></code> Support for `Tool`s using the `VIRTUAL_MACHINE` backend
</summary>





</details>



</details>



---

### `ComputeSupport.Docker`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Docker(
    val enabled: Boolean?,
    val web: Boolean?,
    val vnc: Boolean?,
    val logs: Boolean?,
    val terminal: Boolean?,
    val peers: Boolean?,
    val timeExtension: Boolean?,
    val utilization: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable this feature
</summary>



All other flags are ignored if this is `false`.


</details>

<details>
<summary>
<code>web</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive interface of `WEB` `Application`s
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive interface of `VNC` `Application`s
</summary>





</details>

<details>
<summary>
<code>logs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the log API
</summary>





</details>

<details>
<summary>
<code>terminal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive terminal API
</summary>





</details>

<details>
<summary>
<code>peers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable connection between peering `Job`s
</summary>





</details>

<details>
<summary>
<code>timeExtension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable extension of jobs
</summary>





</details>

<details>
<summary>
<code>utilization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the retrieveUtilization of jobs
</summary>





</details>



</details>



---

### `ComputeSupport.VirtualMachine`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class VirtualMachine(
    val enabled: Boolean?,
    val logs: Boolean?,
    val vnc: Boolean?,
    val terminal: Boolean?,
    val timeExtension: Boolean?,
    val suspension: Boolean?,
    val utilization: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable this feature
</summary>



All other flags are ignored if this is `false`.


</details>

<details>
<summary>
<code>logs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the log API
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the VNC API
</summary>





</details>

<details>
<summary>
<code>terminal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive terminal API
</summary>





</details>

<details>
<summary>
<code>timeExtension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable extension of jobs
</summary>





</details>

<details>
<summary>
<code>suspension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable suspension of jobs
</summary>





</details>

<details>
<summary>
<code>utilization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the retrieveUtilization of jobs
</summary>





</details>



</details>



---

### `CpuAndMemory`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CpuAndMemory(
    val cpu: Double,
    val memory: Long,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>cpu</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code> Number of virtual cores
</summary>



Implement as a floating to represent fractions of a virtual core. This is for example useful for Kubernetes
(and other container systems) which will allocate milli-cpus.


</details>

<details>
<summary>
<code>memory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Memory available in bytes
</summary>





</details>



</details>



---

### `ExportedParameters`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExportedParameters(
    val siteVersion: Int,
    val request: ExportedParametersRequest,
    val resolvedResources: ExportedParameters.Resources?,
    val machineType: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>siteVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>request</code>: <code><code><a href='#exportedparametersrequest'>ExportedParametersRequest</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedResources</code>: <code><code><a href='#exportedparameters.resources'>ExportedParameters.Resources</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>machineType</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

### `ExportedParameters.Resources`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Resources(
    val ingress: JsonObject?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ingress</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>



</details>



---

### `Ingress`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An L7 ingress-point (HTTP)_

```kotlin
data class Ingress(
    val id: String,
    val specification: IngressSpecification,
    val owner: ResourceOwner,
    val createdAt: Long,
    val status: IngressStatus,
    val updates: List<IngressUpdate>?,
    val permissions: ResourcePermissions?,
    val providerGeneratedId: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A unique identifier referencing the `Resource`
</summary>



The ID is unique across a provider for a single resource type.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#ingressspecification'>IngressSpecification</a></code></code>
</summary>





</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> Information about the owner of this resource
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Information about when this resource was created
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#ingressstatus'>IngressStatus</a></code></code> The current status of this resource
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#ingressupdate'>IngressUpdate</a>&gt;?</code></code> A list of updates for this `Ingress`
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `IngressSpecification`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IngressSpecification(
    val domain: String,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>domain</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The domain used for L7 load-balancing for use with this `Ingress`
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code> The product used for the `Ingress`
</summary>





</details>



</details>



---

### `IngressState`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class IngressState {
    PREPARING,
    READY,
    UNAVAILABLE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>PREPARING</code> A state indicating that the `Ingress` is currently being prepared and is expected to reach `READY` soon.
</summary>





</details>

<details>
<summary>
<code>READY</code> A state indicating that the `Ingress` is ready for use or already in use.
</summary>





</details>

<details>
<summary>
<code>UNAVAILABLE</code> A state indicating that the `Ingress` is currently unavailable.
</summary>



This state can be used to indicate downtime or service interruptions by the provider.


</details>



</details>



---

### `IngressStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The status of an `Ingress`_

```kotlin
data class IngressStatus(
    val boundTo: List<String>?,
    val state: IngressState,
    val resolvedSupport: ResolvedSupport<Product.Ingress, IngressSupport>?,
    val resolvedProduct: Product.Ingress?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>boundTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The ID of the `Job` that this `Ingress` is currently bound to
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#ingressstate'>IngressState</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Ingress.md'>Product.Ingress</a>, <a href='#ingresssupport'>IngressSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Ingress.md'>Product.Ingress</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `IngressSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IngressSupport(
    val domainPrefix: String,
    val domainSuffix: String,
    val product: ProductReference,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>domainPrefix</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>domainSuffix</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>



</details>



---

### `IngressUpdate`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IngressUpdate(
    val state: IngressState?,
    val status: String?,
    val timestamp: Long?,
    val binding: JobBinding?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#ingressstate'>IngressState</a>?</code></code> The new state that the `Ingress` transitioned to (if any)
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A new status message for the `Ingress` (if any)
</summary>





</details>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this update was registered by UCloud
</summary>





</details>

<details>
<summary>
<code>binding</code>: <code><code><a href='#jobbinding'>JobBinding</a>?</code></code>
</summary>





</details>



</details>



---

### `JobBindKind`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class JobBindKind {
    BIND,
    UNBIND,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BIND</code>
</summary>





</details>

<details>
<summary>
<code>UNBIND</code>
</summary>





</details>



</details>



---

### `JobBinding`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobBinding(
    val kind: JobBindKind,
    val job: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>kind</code>: <code><code><a href='#jobbindkind'>JobBindKind</a></code></code>
</summary>





</details>

<details>
<summary>
<code>job</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `JobOutput`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobOutput(
    val outputFolder: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>outputFolder</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `JobStatus`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class JobStatus(
    val state: JobState,
    val jobParametersJson: ExportedParameters?,
    val startedAt: Long?,
    val expiresAt: Long?,
    val resolvedApplication: Application?,
    val resolvedSupport: ResolvedSupport<Product.Compute, ComputeSupport>?,
    val resolvedProduct: Product.Compute?,
    val allowRestart: Boolean?,
)
```
The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
this will contain information such as:

- A state value. For example, a compute `Job` might be `RUNNING`
- Key metrics about the resource.
- Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
  should be listed in the `status` section.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#jobstate'>JobState</a></code></code> The current of state of the `Job`.
</summary>



This will match the latest state set in the `updates`


</details>

<details>
<summary>
<code>jobParametersJson</code>: <code><code><a href='#exportedparameters'>ExportedParameters</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>startedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Timestamp matching when the `Job` most recently transitioned to the `RUNNING` state.
</summary>



For `Job`s which suspend this might occur multiple times. This will always point to the latest pointin time it started running.


</details>

<details>
<summary>
<code>expiresAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Timestamp matching when the `Job` is set to expire.
</summary>



This is generally equal to `startedAt + timeAllocation`. Note that this field might be `null` if the `Job` has no associated deadline. For `Job`s that suspend however, this is more likely to beequal to the initial `RUNNING` state + `timeAllocation`.


</details>

<details>
<summary>
<code>resolvedApplication</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.Application.md'>Application</a>?</code></code> The resolved application referenced by `application`.
</summary>



This attribute is not included by default unless `includeApplication` is specified.


</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md'>Product.Compute</a>, <a href='#computesupport'>ComputeSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.Compute.md'>Product.Compute</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>

<details>
<summary>
<code>allowRestart</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `JobUpdate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes an update to the `Resource`_

```kotlin
data class JobUpdate(
    val state: JobState?,
    val outputFolder: String?,
    val status: String?,
    val expectedState: JobState?,
    val expectedDifferentState: Boolean?,
    val newTimeAllocation: Long?,
    val allowRestart: Boolean?,
    val newMounts: List<String>?,
    val timestamp: Long?,
)
```
Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
use the full update history if you only wish to know the _current_ state of a `Resource`.

An update will typically contain information similar to the `status` field, for example:

- A state value. For example, a compute `Job` might be `RUNNING`.
- Change in key metrics.
- Bindings to related `Resource`s.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>state</code>: <code><code><a href='#jobstate'>JobState</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>outputFolder</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A generic text message describing the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>expectedState</code>: <code><code><a href='#jobstate'>JobState</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>expectedDifferentState</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newTimeAllocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowRestart</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp referencing when UCloud received this update
</summary>





</details>



</details>



---

### `JobsLog`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsLog(
    val rank: Int,
    val stdout: String?,
    val stderr: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>stdout</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>stderr</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `OpenSession`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
sealed class OpenSession {
    abstract val jobId: String
    abstract val rank: Int

    class Shell : OpenSession()
    class Web : OpenSession()
    class Vnc : OpenSession()
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `OpenSession.Shell`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Shell(
    val jobId: String,
    val rank: Int,
    val sessionIdentifier: String,
    val type: String /* "shell" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sessionIdentifier</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "shell" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `OpenSession.Vnc`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Vnc(
    val jobId: String,
    val rank: Int,
    val url: String,
    val password: String?,
    val type: String /* "vnc" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>url</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>password</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "vnc" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `OpenSession.Web`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Web(
    val jobId: String,
    val rank: Int,
    val redirectClientTo: String,
    val type: String /* "web" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>redirectClientTo</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "web" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `OpenSessionWithProvider`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class OpenSessionWithProvider(
    val providerDomain: String,
    val providerId: String,
    val session: OpenSession,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>providerDomain</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>providerId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>session</code>: <code><code><a href='#opensession'>OpenSession</a></code></code>
</summary>





</details>



</details>



---

### `QueueStatus`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class QueueStatus(
    val running: Int,
    val pending: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>running</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The number of jobs running in the system
</summary>





</details>

<details>
<summary>
<code>pending</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The number of jobs waiting in the queue
</summary>





</details>



</details>



---

### `ExportedParametersRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExportedParametersRequest(
    val application: NameAndVersion,
    val product: ProductReference,
    val name: String?,
    val replicas: Int,
    val parameters: JsonObject,
    val resources: List<JsonObject>,
    val timeAllocation: SimpleDuration?,
    val resolvedProduct: JsonObject?,
    val resolvedApplication: JsonObject?,
    val resolvedSupport: JsonObject?,
    val allowDuplicateJob: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>application</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.NameAndVersion.md'>NameAndVersion</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>replicas</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resources</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>timeAllocation</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedApplication</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>allowDuplicateJob</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `JobsExtendRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsExtendRequestItem(
    val jobId: String,
    val requestedTime: SimpleDuration,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>requestedTime</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a></code></code>
</summary>





</details>



</details>



---

### `JobsOpenInteractiveSessionRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsOpenInteractiveSessionRequestItem(
    val id: String,
    val rank: Int,
    val sessionType: InteractiveSessionType,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>sessionType</code>: <code><code><a href='#interactivesessiontype'>InteractiveSessionType</a></code></code>
</summary>





</details>



</details>



---

### `JobsRetrieveUtilizationRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsRetrieveUtilizationRequest(
    val jobId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `JobsFollowResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsFollowResponse(
    val updates: List<JobUpdate>,
    val log: List<JobsLog>,
    val newStatus: JobStatus?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobupdate'>JobUpdate</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>log</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobslog'>JobsLog</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>newStatus</code>: <code><code><a href='#jobstatus'>JobStatus</a>?</code></code>
</summary>





</details>



</details>



---

### `JobsRetrieveUtilizationResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsRetrieveUtilizationResponse(
    val capacity: CpuAndMemory,
    val usedCapacity: CpuAndMemory,
    val queueStatus: QueueStatus,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>capacity</code>: <code><code><a href='#cpuandmemory'>CpuAndMemory</a></code></code> The total capacity of the entire compute system
</summary>





</details>

<details>
<summary>
<code>usedCapacity</code>: <code><code><a href='#cpuandmemory'>CpuAndMemory</a></code></code> The capacity currently in use, by running jobs, of the entire compute system
</summary>





</details>

<details>
<summary>
<code>queueStatus</code>: <code><code><a href='#queuestatus'>QueueStatus</a></code></code> The system of the queue
</summary>





</details>



</details>



---

