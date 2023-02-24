[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# Example: Starting an interactive terminal session

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
            cpuModel = null, 
            description = "An example machine", 
            freeToUse = false, 
            gpu = 0, 
            gpuModel = null, 
            hiddenInGrantApplications = false, 
            memoryInGigs = 2, 
            memoryModel = null, 
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
            domainOverride = null, 
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
#                     "cpuModel": null,
#                     "memoryModel": null,
#                     "gpuModel": null,
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
#                     },
#                     "native": {
#                         "enabled": null,
#                         "logs": null,
#                         "vnc": null,
#                         "terminal": null,
#                         "timeExtension": null,
#                         "utilization": null,
#                         "web": null
#                     },
#                     "maintenance": null
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
#                 "sessionIdentifier": "a81ea644-58f5-44d9-8e94-89f81666c441",
#                 "domainOverride": null
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


