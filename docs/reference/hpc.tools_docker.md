[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md)

# Example: Retrieve a container based Tool

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

/* This example show an example Tool which uses a container backend. This Tool specifies that the 
container image is "acme/batch:1.0.0". The provider decides how to retrieve these images. We 
recommend that you follow the standard defined by Docker. */

ToolStore.findByNameAndVersion.call(
    FindByNameAndVersion(
        name = "acme-batch", 
        version = "1.0.0", 
    ),
    user
).orThrow()

/*
Tool(
    createdAt = 1633329776235, 
    description = NormalizedToolDescription(
        authors = listOf("Acme Inc."), 
        backend = ToolBackend.DOCKER, 
        container = null, 
        defaultNumberOfNodes = 1, 
        defaultTimeAllocation = SimpleDuration(
            hours = 1, 
            minutes = 0, 
            seconds = 0, 
        ), 
        description = "A batch tool", 
        image = "acme/batch:1.0.0", 
        info = NameAndVersion(
            name = "acme-batch", 
            version = "1.0.0", 
        ), 
        license = "None", 
        requiredModules = emptyList(), 
        supportedProviders = null, 
        title = "Acme Batch", 
    ), 
    modifiedAt = 1633329776235, 
    owner = "_ucloud", 
)
*/
```


</details>

<details>
<summary>
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example show an example Tool which uses a container backend. This Tool specifies that the 
container image is "acme/batch:1.0.0". The provider decides how to retrieve these images. We 
recommend that you follow the standard defined by Docker. */

// Authenticated as user
await callAPI(HpcToolsApi.findByNameAndVersion(
    {
        "name": "acme-batch",
        "version": "1.0.0"
    }
);

/*
{
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
            "Acme Inc."
        ],
        "title": "Acme Batch",
        "description": "A batch tool",
        "backend": "DOCKER",
        "license": "None",
        "image": "acme/batch:1.0.0",
        "supportedProviders": null
    }
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

# This example show an example Tool which uses a container backend. This Tool specifies that the 
# container image is "acme/batch:1.0.0". The provider decides how to retrieve these images. We 
# recommend that you follow the standard defined by Docker.

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/tools/byNameAndVersion?name=acme-batch&version=1.0.0" 

# {
#     "owner": "_ucloud",
#     "createdAt": 1633329776235,
#     "modifiedAt": 1633329776235,
#     "description": {
#         "info": {
#             "name": "acme-batch",
#             "version": "1.0.0"
#         },
#         "container": null,
#         "defaultNumberOfNodes": 1,
#         "defaultTimeAllocation": {
#             "hours": 1,
#             "minutes": 0,
#             "seconds": 0
#         },
#         "requiredModules": [
#         ],
#         "authors": [
#             "Acme Inc."
#         ],
#         "title": "Acme Batch",
#         "description": "A batch tool",
#         "backend": "DOCKER",
#         "license": "None",
#         "image": "acme/batch:1.0.0",
#         "supportedProviders": null
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/hpc.tools_docker.png)

</details>


