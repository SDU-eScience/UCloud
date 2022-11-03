[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# Example: Simple virtual machine

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

/* This example shows an Application encoding a virtual machine. It will use the
"acme-operating-system" as its base image, as defined in the Tool.  */

AppStore.findByNameAndVersion.call(
    FindApplicationAndOptionalDependencies(
        appName = "acme-os", 
        appVersion = "1.0.0", 
    ),
    user
).orThrow()

/*
ApplicationWithFavoriteAndTags(
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
        invocation = emptyList(), 
        licenseServers = emptyList(), 
        outputFileGlobs = listOf("*"), 
        parameters = emptyList(), 
        shouldAllowAdditionalMounts = false, 
        shouldAllowAdditionalPeers = true, 
        ssh = null, 
        tool = ToolReference(
            name = "acme-os", 
            tool = Tool(
                createdAt = 1633329776235, 
                description = NormalizedToolDescription(
                    authors = listOf("UCloud"), 
                    backend = ToolBackend.VIRTUAL_MACHINE, 
                    container = null, 
                    defaultNumberOfNodes = 1, 
                    defaultTimeAllocation = SimpleDuration(
                        hours = 1, 
                        minutes = 0, 
                        seconds = 0, 
                    ), 
                    description = "An example tool", 
                    image = "acme-operating-system", 
                    info = NameAndVersion(
                        name = "acme-os", 
                        version = "1.0.0", 
                    ), 
                    license = "None", 
                    requiredModules = emptyList(), 
                    supportedProviders = null, 
                    title = "Acme os", 
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
        isPublic = true, 
        name = "acme-os", 
        public = true, 
        title = "Acme os", 
        version = "1.0.0", 
        website = null, 
    ), 
    tags = emptyList(), 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example shows an Application encoding a virtual machine. It will use the
"acme-operating-system" as its base image, as defined in the Tool.  */

// Authenticated as user
await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "acme-os",
        "appVersion": "1.0.0"
    }
);

/*
{
    "metadata": {
        "name": "acme-os",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "Acme os",
        "description": "An example application",
        "website": null,
        "public": true
    },
    "invocation": {
        "tool": {
            "name": "acme-os",
            "version": "1.0.0",
            "tool": {
                "owner": "_ucloud",
                "createdAt": 1633329776235,
                "modifiedAt": 1633329776235,
                "description": {
                    "info": {
                        "name": "acme-os",
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
                    "title": "Acme os",
                    "description": "An example tool",
                    "backend": "VIRTUAL_MACHINE",
                    "license": "None",
                    "image": "acme-operating-system",
                    "supportedProviders": null
                }
            }
        },
        "invocation": [
        ],
        "parameters": [
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
        ]
    },
    "favorite": false,
    "tags": [
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

# This example shows an Application encoding a virtual machine. It will use the
# "acme-operating-system" as its base image, as defined in the Tool. 

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/apps/byNameAndVersion?appName=acme-os&appVersion=1.0.0" 

# {
#     "metadata": {
#         "name": "acme-os",
#         "version": "1.0.0",
#         "authors": [
#             "UCloud"
#         ],
#         "title": "Acme os",
#         "description": "An example application",
#         "website": null,
#         "public": true
#     },
#     "invocation": {
#         "tool": {
#             "name": "acme-os",
#             "version": "1.0.0",
#             "tool": {
#                 "owner": "_ucloud",
#                 "createdAt": 1633329776235,
#                 "modifiedAt": 1633329776235,
#                 "description": {
#                     "info": {
#                         "name": "acme-os",
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
#                     "title": "Acme os",
#                     "description": "An example tool",
#                     "backend": "VIRTUAL_MACHINE",
#                     "license": "None",
#                     "image": "acme-operating-system",
#                     "supportedProviders": null
#                 }
#             }
#         },
#         "invocation": [
#         ],
#         "parameters": [
#         ],
#         "outputFileGlobs": [
#             "*"
#         ],
#         "applicationType": "BATCH",
#         "vnc": null,
#         "web": null,
#         "ssh": null,
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
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/hpc.apps_virtualMachine.png)

</details>


