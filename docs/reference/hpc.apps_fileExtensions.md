[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# Example: Registering a file handler

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

/* This example shows an Application with a graphical web interface. The web server, hosting the 
interface, runs on port 8080 as defined in the `invocation.web` section. */


/* The Application also registers a file handler of all files with the `*.c` extension. This is used as
a hint for the frontend that files with this extension can be opened with this Application. When
opened like this, the file's parent folder will be mounted as a resource. */

AppStore.findByNameAndVersion.call(
    FindApplicationAndOptionalDependencies(
        appName = "acme-web", 
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
        applicationType = ApplicationType.WEB, 
        container = null, 
        environment = null, 
        fileExtensions = listOf(".c"), 
        invocation = listOf(WordInvocationParameter(
            word = "web-server", 
        )), 
        licenseServers = emptyList(), 
        modules = null, 
        outputFileGlobs = listOf("*"), 
        parameters = emptyList(), 
        shouldAllowAdditionalMounts = true, 
        shouldAllowAdditionalPeers = true, 
        ssh = null, 
        tool = ToolReference(
            name = "acme-web", 
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
                    image = "acme/web:1.0.0", 
                    info = NameAndVersion(
                        name = "acme-web", 
                        version = "1.0.0", 
                    ), 
                    license = "None", 
                    requiredModules = emptyList(), 
                    supportedProviders = null, 
                    title = "Acme web", 
                ), 
                modifiedAt = 1633329776235, 
                owner = "_ucloud", 
            ), 
            version = "1.0.0", 
        ), 
        vnc = null, 
        web = WebDescription(
            port = 8080, 
        ), 
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
        name = "acme-web", 
        public = true, 
        title = "Acme web", 
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
<b>Communication Flow:</b> Curl
</summary>

```bash
# ------------------------------------------------------------------------------------------------------
# $host is the UCloud instance to contact. Example: 'http://localhost:8080' or 'https://cloud.sdu.dk'
# $accessToken is a valid access-token issued by UCloud
# ------------------------------------------------------------------------------------------------------

# This example shows an Application with a graphical web interface. The web server, hosting the 
# interface, runs on port 8080 as defined in the `invocation.web` section.

# The Application also registers a file handler of all files with the `*.c` extension. This is used as
# a hint for the frontend that files with this extension can be opened with this Application. When
# opened like this, the file's parent folder will be mounted as a resource.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/apps/byNameAndVersion?appName=acme-web&appVersion=1.0.0" 

# {
#     "metadata": {
#         "name": "acme-web",
#         "version": "1.0.0",
#         "authors": [
#             "UCloud"
#         ],
#         "title": "Acme web",
#         "description": "An example application",
#         "website": null,
#         "public": true,
#         "flavorName": null,
#         "group": {
#             "id": 0,
#             "title": "Test Group",
#             "description": null,
#             "defaultApplication": null,
#             "tags": [
#             ]
#         }
#     },
#     "invocation": {
#         "tool": {
#             "name": "acme-web",
#             "version": "1.0.0",
#             "tool": {
#                 "owner": "_ucloud",
#                 "createdAt": 1633329776235,
#                 "modifiedAt": 1633329776235,
#                 "description": {
#                     "info": {
#                         "name": "acme-web",
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
#                     "title": "Acme web",
#                     "description": "An example tool",
#                     "backend": "DOCKER",
#                     "license": "None",
#                     "image": "acme/web:1.0.0",
#                     "supportedProviders": null
#                 }
#             }
#         },
#         "invocation": [
#             {
#                 "type": "word",
#                 "word": "web-server"
#             }
#         ],
#         "parameters": [
#         ],
#         "outputFileGlobs": [
#             "*"
#         ],
#         "applicationType": "WEB",
#         "vnc": null,
#         "web": {
#             "port": 8080
#         },
#         "ssh": null,
#         "container": null,
#         "environment": null,
#         "allowAdditionalMounts": null,
#         "allowAdditionalPeers": null,
#         "allowMultiNode": false,
#         "allowPublicIp": false,
#         "allowPublicLink": null,
#         "fileExtensions": [
#             ".c"
#         ],
#         "licenseServers": [
#         ],
#         "modules": null
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

![](/docs/diagrams/hpc.apps_fileExtensions.png)

</details>


