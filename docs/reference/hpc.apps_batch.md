[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# Example: Simple batch application

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

/* Applications contain quite a lot of information. The most important pieces of information are
summarized below:

- This Job will run a `BATCH` application
  - See `invocation.applicationType`
  
- The application should launch the `acme/batch:1.0.0` container
  - `invocation.tool.tool.description.backend`
  - `invocation.tool.tool.description.image`
  
- The command-line invocation will look like this: `acme-batch --debug "Hello, World!"`. 
  - The invocation is created from `invocation.invocation`
  - With parameters defined in `invocation.parameters` */

AppStore.findByNameAndVersion.call(
    FindApplicationAndOptionalDependencies(
        appName = "acme-batch", 
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
        isPublic = true, 
        name = "acme-batch", 
        public = true, 
        title = "Acme batch", 
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

/* Applications contain quite a lot of information. The most important pieces of information are
summarized below:

- This Job will run a `BATCH` application
  - See `invocation.applicationType`
  
- The application should launch the `acme/batch:1.0.0` container
  - `invocation.tool.tool.description.backend`
  - `invocation.tool.tool.description.image`
  
- The command-line invocation will look like this: `acme-batch --debug "Hello, World!"`. 
  - The invocation is created from `invocation.invocation`
  - With parameters defined in `invocation.parameters` */

// Authenticated as user
await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "acme-batch",
        "appVersion": "1.0.0"
    }
);

/*
{
    "metadata": {
        "name": "acme-batch",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "Acme batch",
        "description": "An example application",
        "website": null,
        "public": true
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

# Applications contain quite a lot of information. The most important pieces of information are
# summarized below:
# 
# - This Job will run a `BATCH` application
#   - See `invocation.applicationType`
#   
# - The application should launch the `acme/batch:1.0.0` container
#   - `invocation.tool.tool.description.backend`
#   - `invocation.tool.tool.description.image`
#   
# - The command-line invocation will look like this: `acme-batch --debug "Hello, World!"`. 
#   - The invocation is created from `invocation.invocation`
#   - With parameters defined in `invocation.parameters`

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/apps/byNameAndVersion?appName=acme-batch&appVersion=1.0.0" 

# {
#     "metadata": {
#         "name": "acme-batch",
#         "version": "1.0.0",
#         "authors": [
#             "UCloud"
#         ],
#         "title": "Acme batch",
#         "description": "An example application",
#         "website": null,
#         "public": true
#     },
#     "invocation": {
#         "tool": {
#             "name": "acme-batch",
#             "version": "1.0.0",
#             "tool": {
#                 "owner": "_ucloud",
#                 "createdAt": 1633329776235,
#                 "modifiedAt": 1633329776235,
#                 "description": {
#                     "info": {
#                         "name": "acme-batch",
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
#                     "title": "Acme batch",
#                     "description": "An example tool",
#                     "backend": "DOCKER",
#                     "license": "None",
#                     "image": "acme/batch:1.0.0",
#                     "supportedProviders": null
#                 }
#             }
#         },
#         "invocation": [
#             {
#                 "type": "word",
#                 "word": "acme-batch"
#             },
#             {
#                 "type": "var",
#                 "variableNames": [
#                     "debug"
#                 ],
#                 "prefixGlobal": "--debug ",
#                 "suffixGlobal": "",
#                 "prefixVariable": "",
#                 "suffixVariable": "",
#                 "isPrefixVariablePartOfArg": false,
#                 "isSuffixVariablePartOfArg": false
#             },
#             {
#                 "type": "var",
#                 "variableNames": [
#                     "value"
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
#                 "type": "boolean",
#                 "name": "debug",
#                 "optional": false,
#                 "defaultValue": null,
#                 "title": "",
#                 "description": "Should debug be enabled?",
#                 "trueValue": "true",
#                 "falseValue": "false"
#             },
#             {
#                 "type": "text",
#                 "name": "value",
#                 "optional": false,
#                 "defaultValue": null,
#                 "title": "",
#                 "description": "The value for the batch application"
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
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/hpc.apps_batch.png)

</details>


