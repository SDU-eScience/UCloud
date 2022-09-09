<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/providers/shares/outgoing.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/appstore/apps.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / Tools
# Tools

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images)._

## Rationale

All [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)s in UCloud consist of two components: the 
[`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md)  and the [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application..md)  The [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md)  defines the computational environment. This includes
software packages and other assets (e.g. configuration). A typical example would be a base-image for a container or a 
virtual machine. The [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  describes how to invoke the [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md). This includes specifying the 
input parameters and command-line invocation for the [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md).

---
    
__⚠️ WARNING:__ The API listed on this page will likely change to conform with our
[API conventions](/docs/developer-guide/core/api-conventions.md). Be careful when building integrations. The following
changes are expected:

- RPC names will change to conform with the conventions
- RPC request and response types will change to conform with the conventions
- RPCs which return a page will be collapsed into a single `browse` endpoint
- Some property names will change to be consistent with [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)s

---

## Table of Contents
<details>
<summary>
<a href='#example-retrieve-a-container-based-tool'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-retrieve-a-container-based-tool'>Retrieve a container based Tool</a></td></tr>
<tr><td><a href='#example-retrieve-a-virtual-machine-based-tool'>Retrieve a virtual machine based Tool</a></td></tr>
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
<td><a href='#fetchlogo'><code>fetchLogo</code></a></td>
<td>Retrieves a logo associated with a Tool</td>
</tr>
<tr>
<td><a href='#findbyname'><code>findByName</code></a></td>
<td>Finds a Page of Tools which share the same name</td>
</tr>
<tr>
<td><a href='#findbynameandversion'><code>findByNameAndVersion</code></a></td>
<td>Finds a Tool by name and version</td>
</tr>
<tr>
<td><a href='#listall'><code>listAll</code></a></td>
<td>Queries the entire catalog of Tools</td>
</tr>
<tr>
<td><a href='#clearlogo'><code>clearLogo</code></a></td>
<td>Deletes an existing logo from a Tool</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates a new Tool and adds it to the internal catalog</td>
</tr>
<tr>
<td><a href='#uploadlogo'><code>uploadLogo</code></a></td>
<td>Uploads a logo and associates it with a Tool</td>
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
<td><a href='#tool'><code>Tool</code></a></td>
<td>Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images).</td>
</tr>
<tr>
<td><a href='#normalizedtooldescription'><code>NormalizedToolDescription</code></a></td>
<td>The specification of a Tool</td>
</tr>
<tr>
<td><a href='#findbynameandpagination'><code>FindByNameAndPagination</code></a></td>
<td>Request type to find a Page of resources defined by a name</td>
</tr>
<tr>
<td><a href='#findbynameandversion'><code>FindByNameAndVersion</code></a></td>
<td>A request type to find a resource by name and version</td>
</tr>
<tr>
<td><a href='#nameandversion'><code>NameAndVersion</code></a></td>
<td>A type describing a name and version tuple</td>
</tr>
<tr>
<td><a href='#simpleduration'><code>SimpleDuration</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#toolbackend'><code>ToolBackend</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#clearlogorequest'><code>ClearLogoRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#fetchlogorequest'><code>FetchLogoRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#uploadapplicationlogorequest'><code>UploadApplicationLogoRequest</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Retrieve a container based Tool
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


## Example: Retrieve a virtual machine based Tool
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

/* This example show an example Tool which uses a virtual machine backend. The Tool specifies that 
the base image is "acme-operating-system". The provider decides how to retrieve these images. For 
virtual machines, this is likely so dependant on the provider. As a result, we recommend using the 
supportedProviders property.  */

ToolStore.findByNameAndVersion.call(
    FindByNameAndVersion(
        name = "acme-os", 
        version = "1.0.0", 
    ),
    user
).orThrow()

/*
Tool(
    createdAt = 1633329776235, 
    description = NormalizedToolDescription(
        authors = listOf("Acme Inc."), 
        backend = ToolBackend.VIRTUAL_MACHINE, 
        container = null, 
        defaultNumberOfNodes = 1, 
        defaultTimeAllocation = SimpleDuration(
            hours = 1, 
            minutes = 0, 
            seconds = 0, 
        ), 
        description = "A virtual machine tool", 
        image = "acme-operating-system", 
        info = NameAndVersion(
            name = "acme-batch", 
            version = "1.0.0", 
        ), 
        license = "None", 
        requiredModules = emptyList(), 
        supportedProviders = listOf("example"), 
        title = "Acme Operating System", 
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

/* This example show an example Tool which uses a virtual machine backend. The Tool specifies that 
the base image is "acme-operating-system". The provider decides how to retrieve these images. For 
virtual machines, this is likely so dependant on the provider. As a result, we recommend using the 
supportedProviders property.  */

// Authenticated as user
await callAPI(HpcToolsApi.findByNameAndVersion(
    {
        "name": "acme-os",
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
        "title": "Acme Operating System",
        "description": "A virtual machine tool",
        "backend": "VIRTUAL_MACHINE",
        "license": "None",
        "image": "acme-operating-system",
        "supportedProviders": [
            "example"
        ]
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

# This example show an example Tool which uses a virtual machine backend. The Tool specifies that 
# the base image is "acme-operating-system". The provider decides how to retrieve these images. For 
# virtual machines, this is likely so dependant on the provider. As a result, we recommend using the 
# supportedProviders property. 

# Authenticated as user
curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/hpc/tools/byNameAndVersion?name=acme-os&version=1.0.0" 

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
#         "title": "Acme Operating System",
#         "description": "A virtual machine tool",
#         "backend": "VIRTUAL_MACHINE",
#         "license": "None",
#         "image": "acme-operating-system",
#         "supportedProviders": [
#             "example"
#         ]
#     }
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/hpc.tools_virtualMachine.png)

</details>



## Remote Procedure Calls

### `fetchLogo`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a logo associated with a Tool_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#fetchlogorequest'>FetchLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint might return HttpStatusCode(value=404, description=Not Found) if the Tool has no logo


### `findByName`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Finds a Page of Tools which share the same name_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findbynameandpagination'>FindByNameAndPagination</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#tool'>Tool</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findByNameAndVersion`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Finds a Tool by name and version_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findbynameandversion'>FindByNameAndVersion</a></code>|<code><a href='#tool'>Tool</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listAll`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Queries the entire catalog of Tools_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.PaginationRequest.md'>PaginationRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#tool'>Tool</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is not recommended for use and will likely disappear in a future release. The results are
returned in no specific order.


### `clearLogo`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Deletes an existing logo from a Tool_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#clearlogorequest'>ClearLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: SERVICE, ADMIN, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=SERVICE,+ADMIN,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new Tool and adds it to the internal catalog_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadLogo`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: ADMIN, SERVICE, PROVIDER](https://img.shields.io/static/v1?label=Auth&message=ADMIN,+SERVICE,+PROVIDER&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Uploads a logo and associates it with a Tool_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#uploadapplicationlogorequest'>UploadApplicationLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Tool`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images)._

```kotlin
data class Tool(
    val owner: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NormalizedToolDescription,
)
```
See [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md) for a more complete discussion.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The username of the user who created this Tool
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp describing initial creation
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp describing most recent modification (Deprecated, Tools are immutable)
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>description</code>: <code><code><a href='#normalizedtooldescription'>NormalizedToolDescription</a></code></code> The specification for this Tool
</summary>





</details>



</details>



---

### `NormalizedToolDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The specification of a Tool_

```kotlin
data class NormalizedToolDescription(
    val info: NameAndVersion,
    val container: String?,
    val defaultNumberOfNodes: Int,
    val defaultTimeAllocation: SimpleDuration,
    val requiredModules: List<String>,
    val authors: List<String>,
    val title: String,
    val description: String,
    val backend: ToolBackend,
    val license: String,
    val image: String?,
    val supportedProviders: List<String>?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>info</code>: <code><code><a href='#nameandversion'>NameAndVersion</a></code></code> The unique name and version tuple
</summary>





</details>

<details>
<summary>
<code>container</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Deprecated, use image instead.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>defaultNumberOfNodes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The default number of nodes
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>defaultTimeAllocation</code>: <code><code><a href='#simpleduration'>SimpleDuration</a></code></code> The default time allocation to use, if none is specified.
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>requiredModules</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A list of required 'modules'
</summary>



The provider decides how to interpret this value. It is intended to be used with a module system of traditional 
HPC systems.


</details>

<details>
<summary>
<code>authors</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A list of authors
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A title for this Tool used for presentation purposes
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description for this Tool used for presentation purposes
</summary>





</details>

<details>
<summary>
<code>backend</code>: <code><code><a href='#toolbackend'>ToolBackend</a></code></code> The backend to use for this Tool
</summary>





</details>

<details>
<summary>
<code>license</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A license used for this Tool. Used for presentation purposes.
</summary>





</details>

<details>
<summary>
<code>image</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> The 'image' used for this Tool
</summary>



This value depends on the `backend` used for the Tool:

- `DOCKER`: The image is a container image. Typically follows the Docker format.
- `VIRTUAL_MACHINE`: The image is a reference to a base-image

It is always up to the Provider how to interpret this value. We recommend using the `supportedProviders`
property to ensure compatibility.


</details>

<details>
<summary>
<code>supportedProviders</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> A list of supported Providers
</summary>



This property determines which Providers are supported by this Tool. The backend will not allow a user to
launch an Application which uses this Tool on a provider not listed in this value.

If no providers are supplied, then this Tool will implicitly support all Providers.


</details>



</details>



---

### `FindByNameAndPagination`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Request type to find a Page of resources defined by a name_

```kotlin
data class FindByNameAndPagination(
    val appName: String,
    val itemsPerPage: Int?,
    val page: Int?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>appName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>page</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `FindByNameAndVersion`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A request type to find a resource by name and version_

```kotlin
data class FindByNameAndVersion(
    val name: String,
    val version: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `NameAndVersion`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A type describing a name and version tuple_

```kotlin
data class NameAndVersion(
    val name: String,
    val version: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `SimpleDuration`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SimpleDuration(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>hours</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>minutes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>seconds</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>



</details>



---

### `ToolBackend`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ToolBackend {
    SINGULARITY,
    DOCKER,
    VIRTUAL_MACHINE,
    NATIVE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>SINGULARITY</code>
</summary>





</details>

<details>
<summary>
<code>DOCKER</code>
</summary>





</details>

<details>
<summary>
<code>VIRTUAL_MACHINE</code>
</summary>





</details>

<details>
<summary>
<code>NATIVE</code>
</summary>





</details>



</details>



---

### `ClearLogoRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ClearLogoRequest(
    val name: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FetchLogoRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FetchLogoRequest(
    val name: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `UploadApplicationLogoRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UploadApplicationLogoRequest(
    val name: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

