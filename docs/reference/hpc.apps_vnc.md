[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# Example: Simple remote desktop application (VNC)

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

/* This example shows an Application with a graphical web interface. The VNC server, hosting the 
interface, runs on port 5900 as defined in the `invocation.vnc` section. */

AppStore.findByNameAndVersion.call(
    FindApplicationAndOptionalDependencies(
        appName = "acme-remote-desktop", 
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
        applicationType = ApplicationType.VNC, 
        container = null, 
        environment = null, 
        fileExtensions = emptyList(), 
        invocation = listOf(WordInvocationParameter(
            word = "vnc-server", 
        )), 
        licenseServers = emptyList(), 
        outputFileGlobs = listOf("*"), 
        parameters = emptyList(), 
        shouldAllowAdditionalMounts = true, 
        shouldAllowAdditionalPeers = true, 
        tool = ToolReference(
            name = "acme-remote-desktop", 
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
                    image = "acme/remote-desktop:1.0.0", 
                    info = NameAndVersion(
                        name = "acme-remote-desktop", 
                        version = "1.0.0", 
                    ), 
                    license = "None", 
                    requiredModules = emptyList(), 
                    supportedProviders = null, 
                    title = "Acme remote desktop", 
                ), 
                modifiedAt = 1633329776235, 
                owner = "_ucloud", 
            ), 
            version = "1.0.0", 
        ), 
        vnc = VncDescription(
            password = null, 
            port = 5900, 
        ), 
        web = null, 
    ), 
    metadata = ApplicationMetadata(
        authors = listOf("UCloud"), 
        description = "An example application", 
        isPublic = true, 
        name = "acme-remote-desktop", 
        public = true, 
        title = "Acme remote desktop", 
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

/* This example shows an Application with a graphical web interface. The VNC server, hosting the 
interface, runs on port 5900 as defined in the `invocation.vnc` section. */

// Authenticated as user
await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "acme-remote-desktop",
        "appVersion": "1.0.0"
    }
);

/*
{
    "metadata": {
        "name": "acme-remote-desktop",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "Acme remote desktop",
        "description": "An example application",
        "website": null,
        "public": true
    },
    "invocation": {
        "tool": {
            "name": "acme-remote-desktop",
            "version": "1.0.0",
            "tool": {
                "owner": "_ucloud",
                "createdAt": 1633329776235,
                "modifiedAt": 1633329776235,
                "description": {
                    "info": {
                        "name": "acme-remote-desktop",
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
                    "title": "Acme remote desktop",
                    "description": "An example tool",
                    "backend": "DOCKER",
                    "license": "None",
                    "image": "acme/remote-desktop:1.0.0",
                    "supportedProviders": null
                }
            }
        },
        "invocation": [
            {
                "type": "word",
                "word": "vnc-server"
            }
        ],
        "parameters": [
        ],
        "outputFileGlobs": [
            "*"
        ],
        "applicationType": "VNC",
        "vnc": {
            "password": null,
            "port": 5900
        },
        "web": null,
        "container": null,
        "environment": null,
        "allowAdditionalMounts": null,
        "allowAdditionalPeers": null,
        "allowMultiNode": false,
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

# This example shows an Application with a graphical web interface. The VNC server, hosting the 
# interface, runs on port 5900 as defined in the `invocation.vnc` section.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/apps/byNameAndVersion?appName=acme-remote-desktop&appVersion=1.0.0" 

# {
#     "metadata": {
#         "name": "acme-remote-desktop",
#         "version": "1.0.0",
#         "authors": [
#             "UCloud"
#         ],
#         "title": "Acme remote desktop",
#         "description": "An example application",
#         "website": null,
#         "public": true
#     },
#     "invocation": {
#         "tool": {
#             "name": "acme-remote-desktop",
#             "version": "1.0.0",
#             "tool": {
#                 "owner": "_ucloud",
#                 "createdAt": 1633329776235,
#                 "modifiedAt": 1633329776235,
#                 "description": {
#                     "info": {
#                         "name": "acme-remote-desktop",
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
#                     "title": "Acme remote desktop",
#                     "description": "An example tool",
#                     "backend": "DOCKER",
#                     "license": "None",
#                     "image": "acme/remote-desktop:1.0.0",
#                     "supportedProviders": null
#                 }
#             }
#         },
#         "invocation": [
#             {
#                 "type": "word",
#                 "word": "vnc-server"
#             }
#         ],
#         "parameters": [
#         ],
#         "outputFileGlobs": [
#             "*"
#         ],
#         "applicationType": "VNC",
#         "vnc": {
#             "password": null,
#             "port": 5900
#         },
#         "web": null,
#         "container": null,
#         "environment": null,
#         "allowAdditionalMounts": null,
#         "allowAdditionalPeers": null,
#         "allowMultiNode": false,
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

![](/docs/diagrams/hpc.apps_vnc.png)

</details>


