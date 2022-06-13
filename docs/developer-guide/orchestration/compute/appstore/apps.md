<p align='center'>
<a href='/docs/developer-guide/orchestration/compute/appstore/tools.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/compute/jobs.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / Applications
# Applications

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Applications specify the input parameters and invocation of a software package._

## Rationale

All [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)s in UCloud consist of two components: the 
[`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md)  and the [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application..md)  The [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md)  defines the computational environment. This includes
software packages and other assets (e.g. configuration). A typical example would be a base-image for a container or a 
virtual machine. The [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  describes how to invoke the [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md). This includes specifying the 
input parameters and command-line invocation for the [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md).

In concrete terms, the ["invocation"](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationInvocationDescription.md) of an [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md) 
covers:

- [Mandatory and optional input parameters.](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md) For example: text and numeric values,
  command-line flags and input files.
- [The command-line invocation, using values from the input parameters.](/docs/reference/dk.sdu.cloud.app.store.api.InvocationParameter.md)
- [Resources attached to the compute environment.](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md) For example: files, 
  IP addresses and software licenses.
- [An application type](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationType.md), defining how the user interacts with it. For example: Batch,
  web and remote desktop (VNC).

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
<a href='#example-simple-batch-application'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-simple-batch-application'>Simple batch application</a></td></tr>
<tr><td><a href='#example-simple-virtual-machine'>Simple virtual machine</a></td></tr>
<tr><td><a href='#example-simple-web-application'>Simple web application</a></td></tr>
<tr><td><a href='#example-simple-remote-desktop-application-(vnc)'>Simple remote desktop application (VNC)</a></td></tr>
<tr><td><a href='#example-registering-a-file-handler'>Registering a file handler</a></td></tr>
<tr><td><a href='#example-an-application-with-default-values'>An Application with default values</a></td></tr>
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
<td><a href='#advancedsearch'><code>advancedSearch</code></a></td>
<td>Searches in the Application catalog using more advanced parameters</td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates a new Application and inserts it into the catalog</td>
</tr>
<tr>
<td><a href='#fetchlogo'><code>fetchLogo</code></a></td>
<td>Retrieves a logo associated with an Application</td>
</tr>
<tr>
<td><a href='#findbyname'><code>findByName</code></a></td>
<td>Finds Applications given an exact name</td>
</tr>
<tr>
<td><a href='#findbynameandversion'><code>findByNameAndVersion</code></a></td>
<td>Retrieves an Application by name and version</td>
</tr>
<tr>
<td><a href='#findbysupportedfileextension'><code>findBySupportedFileExtension</code></a></td>
<td>Finds a page of Application which can open a specific UFile</td>
</tr>
<tr>
<td><a href='#findlatestbytool'><code>findLatestByTool</code></a></td>
<td>Retrieves the latest version of an Application using a specific tool</td>
</tr>
<tr>
<td><a href='#haspermission'><code>hasPermission</code></a></td>
<td>Check if an entity has permission to use a specific Application</td>
</tr>
<tr>
<td><a href='#ispublic'><code>isPublic</code></a></td>
<td>Checks if an Application is publicly accessible</td>
</tr>
<tr>
<td><a href='#listacl'><code>listAcl</code></a></td>
<td>Retrieves the permission information associated with an Application</td>
</tr>
<tr>
<td><a href='#listall'><code>listAll</code></a></td>
<td>Lists all Applications</td>
</tr>
<tr>
<td><a href='#retrievefavorites'><code>retrieveFavorites</code></a></td>
<td>Retrieves the list of favorite Applications for the curent user</td>
</tr>
<tr>
<td><a href='#searchapps'><code>searchApps</code></a></td>
<td>Searches in the Application catalog using a free-text query</td>
</tr>
<tr>
<td><a href='#searchtags'><code>searchTags</code></a></td>
<td>Browses the Application catalog by tag</td>
</tr>
<tr>
<td><a href='#clearlogo'><code>clearLogo</code></a></td>
<td>Removes a logo associated with an Application</td>
</tr>
<tr>
<td><a href='#createtag'><code>createTag</code></a></td>
<td>Attaches a set of tags to an Application</td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td>Removes an Application from the catalog</td>
</tr>
<tr>
<td><a href='#removetag'><code>removeTag</code></a></td>
<td>Removes a set of tags from an Application</td>
</tr>
<tr>
<td><a href='#setpublic'><code>setPublic</code></a></td>
<td>Changes the 'publicly accessible' status of an Application</td>
</tr>
<tr>
<td><a href='#togglefavorite'><code>toggleFavorite</code></a></td>
<td>Toggles the favorite status of an Application for the current user</td>
</tr>
<tr>
<td><a href='#updateacl'><code>updateAcl</code></a></td>
<td>Updates the permissions associated with an Application</td>
</tr>
<tr>
<td><a href='#uploadlogo'><code>uploadLogo</code></a></td>
<td>Uploads a logo and associates it with an Application</td>
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
<td><a href='#application'><code>Application</code></a></td>
<td>Applications specify the input parameters and invocation of a software package.</td>
</tr>
<tr>
<td><a href='#applicationmetadata'><code>ApplicationMetadata</code></a></td>
<td>Metadata associated with an Application</td>
</tr>
<tr>
<td><a href='#applicationinvocationdescription'><code>ApplicationInvocationDescription</code></a></td>
<td>The specification for how to invoke an Application</td>
</tr>
<tr>
<td><a href='#applicationtype'><code>ApplicationType</code></a></td>
<td>The ApplicationType determines how user's interact with an Application</td>
</tr>
<tr>
<td><a href='#vncdescription'><code>VncDescription</code></a></td>
<td>Information to the Provider about how to reach the VNC services</td>
</tr>
<tr>
<td><a href='#webdescription'><code>WebDescription</code></a></td>
<td>Information to the Provider about how to reach the web services</td>
</tr>
<tr>
<td><a href='#containerdescription'><code>ContainerDescription</code></a></td>
<td>Information to the Provider about how to launch the container</td>
</tr>
<tr>
<td><a href='#applicationparameter'><code>ApplicationParameter</code></a></td>
<td>An ApplicationParameter describe a single input parameter to an Application.</td>
</tr>
<tr>
<td><a href='#applicationparameter.textarea'><code>ApplicationParameter.TextArea</code></a></td>
<td>An ApplicationParameter describe a single input parameter to an Application.</td>
</tr>
<tr>
<td><a href='#applicationparameter.bool'><code>ApplicationParameter.Bool</code></a></td>
<td>An input parameter which accepts any boolean value</td>
</tr>
<tr>
<td><a href='#applicationparameter.enumeration'><code>ApplicationParameter.Enumeration</code></a></td>
<td>An input parameter which accepts an enum</td>
</tr>
<tr>
<td><a href='#applicationparameter.floatingpoint'><code>ApplicationParameter.FloatingPoint</code></a></td>
<td>An input parameter which accepts any floating point value</td>
</tr>
<tr>
<td><a href='#applicationparameter.ingress'><code>ApplicationParameter.Ingress</code></a></td>
<td>An input parameter which accepts a ingress (public link)</td>
</tr>
<tr>
<td><a href='#applicationparameter.inputdirectory'><code>ApplicationParameter.InputDirectory</code></a></td>
<td>An input parameter which accepts UFiles of type `DIRECTORY`</td>
</tr>
<tr>
<td><a href='#applicationparameter.inputfile'><code>ApplicationParameter.InputFile</code></a></td>
<td>An input parameter which accepts UFiles of type `FILE`</td>
</tr>
<tr>
<td><a href='#applicationparameter.integer'><code>ApplicationParameter.Integer</code></a></td>
<td>An input parameter which accepts any integer value</td>
</tr>
<tr>
<td><a href='#applicationparameter.licenseserver'><code>ApplicationParameter.LicenseServer</code></a></td>
<td>An input parameter which accepts a license</td>
</tr>
<tr>
<td><a href='#applicationparameter.networkip'><code>ApplicationParameter.NetworkIP</code></a></td>
<td>An input parameter which accepts an IP address</td>
</tr>
<tr>
<td><a href='#applicationparameter.peer'><code>ApplicationParameter.Peer</code></a></td>
<td>An input parameter which accepts a peering Job</td>
</tr>
<tr>
<td><a href='#applicationparameter.text'><code>ApplicationParameter.Text</code></a></td>
<td>An input parameter which accepts text</td>
</tr>
<tr>
<td><a href='#appparametervalue'><code>AppParameterValue</code></a></td>
<td>An `AppParameterValue` is value which is supplied to a parameter of an `Application`.</td>
</tr>
<tr>
<td><a href='#appparametervalue.blockstorage'><code>AppParameterValue.BlockStorage</code></a></td>
<td>A reference to block storage (Not yet implemented)</td>
</tr>
<tr>
<td><a href='#appparametervalue.bool'><code>AppParameterValue.Bool</code></a></td>
<td>A boolean value (true or false)</td>
</tr>
<tr>
<td><a href='#appparametervalue.file'><code>AppParameterValue.File</code></a></td>
<td>A reference to a UCloud file</td>
</tr>
<tr>
<td><a href='#appparametervalue.floatingpoint'><code>AppParameterValue.FloatingPoint</code></a></td>
<td>A floating point value</td>
</tr>
<tr>
<td><a href='#appparametervalue.ingress'><code>AppParameterValue.Ingress</code></a></td>
<td>A reference to an HTTP ingress, registered locally at the provider</td>
</tr>
<tr>
<td><a href='#appparametervalue.integer'><code>AppParameterValue.Integer</code></a></td>
<td>An integral value</td>
</tr>
<tr>
<td><a href='#appparametervalue.license'><code>AppParameterValue.License</code></a></td>
<td>A reference to a software license, registered locally at the provider</td>
</tr>
<tr>
<td><a href='#appparametervalue.network'><code>AppParameterValue.Network</code></a></td>
<td>A reference to block storage (Not yet implemented)</td>
</tr>
<tr>
<td><a href='#appparametervalue.peer'><code>AppParameterValue.Peer</code></a></td>
<td>A reference to a separate UCloud `Job`</td>
</tr>
<tr>
<td><a href='#appparametervalue.text'><code>AppParameterValue.Text</code></a></td>
<td>A textual value</td>
</tr>
<tr>
<td><a href='#invocationparameter'><code>InvocationParameter</code></a></td>
<td>InvocationParameters supply values to either the command-line or environment variables.</td>
</tr>
<tr>
<td><a href='#booleanflagparameter'><code>BooleanFlagParameter</code></a></td>
<td>Produces a toggleable command-line flag</td>
</tr>
<tr>
<td><a href='#environmentvariableparameter'><code>EnvironmentVariableParameter</code></a></td>
<td>Produces an environment variable (TODO Documentation)</td>
</tr>
<tr>
<td><a href='#variableinvocationparameter'><code>VariableInvocationParameter</code></a></td>
<td>An InvocationParameter which produces value(s) from parameters.</td>
</tr>
<tr>
<td><a href='#wordinvocationparameter'><code>WordInvocationParameter</code></a></td>
<td>A static value for an InvocationParameter</td>
</tr>
<tr>
<td><a href='#accessentity'><code>AccessEntity</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#appparametervalue.textarea'><code>AppParameterValue.TextArea</code></a></td>
<td>A textual value</td>
</tr>
<tr>
<td><a href='#applicationaccessright'><code>ApplicationAccessRight</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#applicationparameter.enumoption'><code>ApplicationParameter.EnumOption</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#applicationsummarywithfavorite'><code>ApplicationSummaryWithFavorite</code></a></td>
<td>Applications specify the input parameters and invocation of a software package.</td>
</tr>
<tr>
<td><a href='#applicationwithextension'><code>ApplicationWithExtension</code></a></td>
<td>Applications specify the input parameters and invocation of a software package.</td>
</tr>
<tr>
<td><a href='#applicationwithfavoriteandtags'><code>ApplicationWithFavoriteAndTags</code></a></td>
<td>Applications specify the input parameters and invocation of a software package.</td>
</tr>
<tr>
<td><a href='#detailedaccessentity'><code>DetailedAccessEntity</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#detailedentitywithpermission'><code>DetailedEntityWithPermission</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findapplicationandoptionaldependencies'><code>FindApplicationAndOptionalDependencies</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findbysupportedfileextension'><code>FindBySupportedFileExtension</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#project'><code>Project</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#toolreference'><code>ToolReference</code></a></td>
<td>A reference to a Tool</td>
</tr>
<tr>
<td><a href='#aclentryrequest'><code>ACLEntryRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#advancedsearchrequest'><code>AdvancedSearchRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#appsearchrequest'><code>AppSearchRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#createtagsrequest'><code>CreateTagsRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deleteapprequest'><code>DeleteAppRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#favoriterequest'><code>FavoriteRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findlatestbytoolrequest'><code>FindLatestByToolRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#haspermissionrequest'><code>HasPermissionRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ispublicrequest'><code>IsPublicRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#listaclrequest'><code>ListAclRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#setpublicrequest'><code>SetPublicRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#tagsearchrequest'><code>TagSearchRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#updateaclrequest'><code>UpdateAclRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#ispublicresponse'><code>IsPublicResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Simple batch application
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


## Example: Simple virtual machine
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


## Example: Simple web application
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
        fileExtensions = emptyList(), 
        invocation = listOf(WordInvocationParameter(
            word = "web-server", 
        )), 
        licenseServers = emptyList(), 
        outputFileGlobs = listOf("*"), 
        parameters = emptyList(), 
        shouldAllowAdditionalMounts = true, 
        shouldAllowAdditionalPeers = true, 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example shows an Application with a graphical web interface. The web server, hosting the 
interface, runs on port 8080 as defined in the `invocation.web` section. */

// Authenticated as user
await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "acme-web",
        "appVersion": "1.0.0"
    }
);

/*
{
    "metadata": {
        "name": "acme-web",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "Acme web",
        "description": "An example application",
        "website": null,
        "public": true
    },
    "invocation": {
        "tool": {
            "name": "acme-web",
            "version": "1.0.0",
            "tool": {
                "owner": "_ucloud",
                "createdAt": 1633329776235,
                "modifiedAt": 1633329776235,
                "description": {
                    "info": {
                        "name": "acme-web",
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
                    "title": "Acme web",
                    "description": "An example tool",
                    "backend": "DOCKER",
                    "license": "None",
                    "image": "acme/web:1.0.0",
                    "supportedProviders": null
                }
            }
        },
        "invocation": [
            {
                "type": "word",
                "word": "web-server"
            }
        ],
        "parameters": [
        ],
        "outputFileGlobs": [
            "*"
        ],
        "applicationType": "WEB",
        "vnc": null,
        "web": {
            "port": 8080
        },
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

# This example shows an Application with a graphical web interface. The web server, hosting the 
# interface, runs on port 8080 as defined in the `invocation.web` section.

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
#         "public": true
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

![](/docs/diagrams/hpc.apps_web.png)

</details>


## Example: Simple remote desktop application (VNC)
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
        allowPublicIp = false, 
        allowPublicLink = null, 
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

![](/docs/diagrams/hpc.apps_vnc.png)

</details>


## Example: Registering a file handler
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
        outputFileGlobs = listOf("*"), 
        parameters = emptyList(), 
        shouldAllowAdditionalMounts = true, 
        shouldAllowAdditionalPeers = true, 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example shows an Application with a graphical web interface. The web server, hosting the 
interface, runs on port 8080 as defined in the `invocation.web` section. */


/* The Application also registers a file handler of all files with the `*.c` extension. This is used as
a hint for the frontend that files with this extension can be opened with this Application. When
opened like this, the file's parent folder will be mounted as a resource. */

// Authenticated as user
await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "acme-web",
        "appVersion": "1.0.0"
    }
);

/*
{
    "metadata": {
        "name": "acme-web",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "Acme web",
        "description": "An example application",
        "website": null,
        "public": true
    },
    "invocation": {
        "tool": {
            "name": "acme-web",
            "version": "1.0.0",
            "tool": {
                "owner": "_ucloud",
                "createdAt": 1633329776235,
                "modifiedAt": 1633329776235,
                "description": {
                    "info": {
                        "name": "acme-web",
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
                    "title": "Acme web",
                    "description": "An example tool",
                    "backend": "DOCKER",
                    "license": "None",
                    "image": "acme/web:1.0.0",
                    "supportedProviders": null
                }
            }
        },
        "invocation": [
            {
                "type": "word",
                "word": "web-server"
            }
        ],
        "parameters": [
        ],
        "outputFileGlobs": [
            "*"
        ],
        "applicationType": "WEB",
        "vnc": null,
        "web": {
            "port": 8080
        },
        "container": null,
        "environment": null,
        "allowAdditionalMounts": null,
        "allowAdditionalPeers": null,
        "allowMultiNode": false,
        "allowPublicIp": false,
        "allowPublicLink": null,
        "fileExtensions": [
            ".c"
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
#         "public": true
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

![](/docs/diagrams/hpc.apps_fileExtensions.png)

</details>


## Example: An Application with default values
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

/* This example shows an Application which has a single input parameter. The parameter contains a 
textual value. If the user does not provide a specific value, it will default to 'hello'. UCloud 
passes this value as the first argument on the command-line. */

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
        fileExtensions = emptyList(), 
        invocation = listOf(WordInvocationParameter(
            word = "web-server", 
        ), VariableInvocationParameter(
            isPrefixVariablePartOfArg = false, 
            isSuffixVariablePartOfArg = false, 
            prefixGlobal = "", 
            prefixVariable = "", 
            suffixGlobal = "", 
            suffixVariable = "", 
            variableNames = listOf("variable"), 
        )), 
        licenseServers = emptyList(), 
        outputFileGlobs = listOf("*"), 
        parameters = listOf(ApplicationParameter.Text(
            defaultValue = JsonObject(mapOf("type" to JsonLiteral(
                content = "text", 
                isString = true, 
            )),"value" to JsonLiteral(
                content = "hello", 
                isString = true, 
            )),)), 
            description = "A variable passed to the Application (default = 'hello')", 
            name = "variable", 
            optional = true, 
            title = "My Variable", 
        )), 
        shouldAllowAdditionalMounts = true, 
        shouldAllowAdditionalPeers = true, 
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* This example shows an Application which has a single input parameter. The parameter contains a 
textual value. If the user does not provide a specific value, it will default to 'hello'. UCloud 
passes this value as the first argument on the command-line. */

// Authenticated as user
await callAPI(HpcAppsApi.findByNameAndVersion(
    {
        "appName": "acme-web",
        "appVersion": "1.0.0"
    }
);

/*
{
    "metadata": {
        "name": "acme-web",
        "version": "1.0.0",
        "authors": [
            "UCloud"
        ],
        "title": "Acme web",
        "description": "An example application",
        "website": null,
        "public": true
    },
    "invocation": {
        "tool": {
            "name": "acme-web",
            "version": "1.0.0",
            "tool": {
                "owner": "_ucloud",
                "createdAt": 1633329776235,
                "modifiedAt": 1633329776235,
                "description": {
                    "info": {
                        "name": "acme-web",
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
                    "title": "Acme web",
                    "description": "An example tool",
                    "backend": "DOCKER",
                    "license": "None",
                    "image": "acme/web:1.0.0",
                    "supportedProviders": null
                }
            }
        },
        "invocation": [
            {
                "type": "word",
                "word": "web-server"
            },
            {
                "type": "var",
                "variableNames": [
                    "variable"
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
                "name": "variable",
                "optional": true,
                "defaultValue": {
                    "type": "text",
                    "value": "hello"
                },
                "title": "My Variable",
                "description": "A variable passed to the Application (default = 'hello')"
            }
        ],
        "outputFileGlobs": [
            "*"
        ],
        "applicationType": "WEB",
        "vnc": null,
        "web": {
            "port": 8080
        },
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

# This example shows an Application which has a single input parameter. The parameter contains a 
# textual value. If the user does not provide a specific value, it will default to 'hello'. UCloud 
# passes this value as the first argument on the command-line.

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
#         "public": true
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
#             },
#             {
#                 "type": "var",
#                 "variableNames": [
#                     "variable"
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
#                 "name": "variable",
#                 "optional": true,
#                 "defaultValue": {
#                     "type": "text",
#                     "value": "hello"
#                 },
#                 "title": "My Variable",
#                 "description": "A variable passed to the Application (default = 'hello')"
#             }
#         ],
#         "outputFileGlobs": [
#             "*"
#         ],
#         "applicationType": "WEB",
#         "vnc": null,
#         "web": {
#             "port": 8080
#         },
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

![](/docs/diagrams/hpc.apps_defaultValues.png)

</details>



## Remote Procedure Calls

### `advancedSearch`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches in the Application catalog using more advanced parameters_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#advancedsearchrequest'>AdvancedSearchRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates a new Application and inserts it into the catalog_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `fetchLogo`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves a logo associated with an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.app.store.api.FetchLogoRequest.md'>FetchLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findByName`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Finds Applications given an exact name_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.app.store.api.FindByNameAndPagination.md'>FindByNameAndPagination</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findByNameAndVersion`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves an Application by name and version_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findapplicationandoptionaldependencies'>FindApplicationAndOptionalDependencies</a></code>|<code><a href='#applicationwithfavoriteandtags'>ApplicationWithFavoriteAndTags</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findBySupportedFileExtension`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Finds a page of Application which can open a specific UFile_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findbysupportedfileextension'>FindBySupportedFileExtension</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#applicationwithextension'>ApplicationWithExtension</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findLatestByTool`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves the latest version of an Application using a specific tool_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findlatestbytoolrequest'>FindLatestByToolRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#application'>Application</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `hasPermission`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Check if an entity has permission to use a specific Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#haspermissionrequest'>HasPermissionRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `isPublic`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Checks if an Application is publicly accessible_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#ispublicrequest'>IsPublicRequest</a></code>|<code><a href='#ispublicresponse'>IsPublicResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listAcl`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves the permission information associated with an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#listaclrequest'>ListAclRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#detailedentitywithpermission'>DetailedEntityWithPermission</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `listAll`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Lists all Applications_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.PaginationRequest.md'>PaginationRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Results are not ordered in any specific fashion


### `retrieveFavorites`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves the list of favorite Applications for the curent user_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.PaginationRequest.md'>PaginationRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `searchApps`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches in the Application catalog using a free-text query_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#appsearchrequest'>AppSearchRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `searchTags`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the Application catalog by tag_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#tagsearchrequest'>TagSearchRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.Page.md'>Page</a>&lt;<a href='#applicationsummarywithfavorite'>ApplicationSummaryWithFavorite</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `clearLogo`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Removes a logo associated with an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.app.store.api.ClearLogoRequest.md'>ClearLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `createTag`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Attaches a set of tags to an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#createtagsrequest'>CreateTagsRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Admin](https://img.shields.io/static/v1?label=Auth&message=Admin&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Removes an Application from the catalog_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#deleteapprequest'>DeleteAppRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `removeTag`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Removes a set of tags from an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#createtagsrequest'>CreateTagsRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `setPublic`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Changes the 'publicly accessible' status of an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#setpublicrequest'>SetPublicRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `toggleFavorite`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Toggles the favorite status of an Application for the current user_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#favoriterequest'>FavoriteRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `updateAcl`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the permissions associated with an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#updateaclrequest'>UpdateAclRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `uploadLogo`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Uploads a logo and associates it with an Application_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.app.store.api.UploadApplicationLogoRequest.md'>UploadApplicationLogoRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Application`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Applications specify the input parameters and invocation of a software package._

```kotlin
data class Application(
    val metadata: ApplicationMetadata,
    val invocation: ApplicationInvocationDescription,
)
```
For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#applicationmetadata'>ApplicationMetadata</a></code></code>
</summary>





</details>

<details>
<summary>
<code>invocation</code>: <code><code><a href='#applicationinvocationdescription'>ApplicationInvocationDescription</a></code></code>
</summary>





</details>



</details>



---

### `ApplicationMetadata`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Metadata associated with an Application_

```kotlin
data class ApplicationMetadata(
    val name: String,
    val version: String,
    val authors: List<String>,
    val title: String,
    val description: String,
    val website: String?,
    val public: Boolean,
    val isPublic: Boolean,
)
```
The metadata describes information mostly useful for presentation purposes. The only exception are `name` and
`version` which are (also) used as identifiers.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A stable identifier for this Application's name
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A stable identifier for this Application's version
</summary>





</details>

<details>
<summary>
<code>authors</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code> A list of authors
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A (non-stable) title for this Application, used for presentation
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A markdown document describing this Application
</summary>





</details>

<details>
<summary>
<code>website</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> An absolute URL which points to further information about the Application
</summary>





</details>

<details>
<summary>
<code>public</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> A flag which describes if this Application is publicly accessible
</summary>





</details>

<details>
<summary>
<code>isPublic</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>



</details>



---

### `ApplicationInvocationDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The specification for how to invoke an Application_

```kotlin
data class ApplicationInvocationDescription(
    val tool: ToolReference,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter>,
    val outputFileGlobs: List<String>,
    val applicationType: ApplicationType?,
    val vnc: VncDescription?,
    val web: WebDescription?,
    val container: ContainerDescription?,
    val environment: JsonObject?,
    val allowAdditionalMounts: Boolean?,
    val allowAdditionalPeers: Boolean?,
    val allowMultiNode: Boolean?,
    val allowPublicIp: Boolean?,
    val allowPublicLink: Boolean?,
    val fileExtensions: List<String>?,
    val licenseServers: List<String>?,
    val shouldAllowAdditionalMounts: Boolean,
    val shouldAllowAdditionalPeers: Boolean,
)
```
All [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)s require a `tool`. The [`Tool`](/docs/reference/dk.sdu.cloud.app.store.api.Tool.md)  specify the concrete computing environment. 
With the `tool` we get the required software packages and configuration.

In this environment, we must start some software. Any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  launched with
this [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  will only run for as long as the software runs. You can specify the command-line 
invocation through the `invocation` property. Each element in this list produce zero or more arguments for the 
actual invocation. These [`InvocationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.InvocationParameter.md)s can reference the input `parameters` of the 
[`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md). In addition, you can set the `environment` variables through the same mechanism.

All [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)s have an [`ApplicationType`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationType.md)  associated with them. This `type` determines how the 
user interacts with your [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md). We support the following types:

- `BATCH`: A non-interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  which runs without user input
- `VNC`: An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a remote desktop interface
- `WEB`:  An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a graphical web interface

The [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  must expose information about how to access interactive services. It can do so by 
setting `vnc` and `web`. Providers must use this information when 
[opening an interactive session](/docs/reference/jobs.openInteractiveSession.md). 

Users can launch a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  with additional `resources`, such as 
IP addresses and files. The [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  author specifies the supported resources through the 
`allowXXX` properties.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>tool</code>: <code><code><a href='#toolreference'>ToolReference</a></code></code> A reference to the Tool used by this Application
</summary>





</details>

<details>
<summary>
<code>invocation</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#invocationparameter'>InvocationParameter</a>&gt;</code></code> Instructions on how to build the command-line invocation
</summary>





</details>

<details>
<summary>
<code>parameters</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationparameter'>ApplicationParameter</a>&gt;</code></code> The input parameters used by this Application
</summary>





</details>

<details>
<summary>
<code>outputFileGlobs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>applicationType</code>: <code><code><a href='#applicationtype'>ApplicationType</a>?</code></code> The type of this Application, it determines how users will interact with the Application
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='#vncdescription'>VncDescription</a>?</code></code> Information about how to reach the VNC service
</summary>





</details>

<details>
<summary>
<code>web</code>: <code><code><a href='#webdescription'>WebDescription</a>?</code></code> Information about how to reach the web service
</summary>





</details>

<details>
<summary>
<code>container</code>: <code><code><a href='#containerdescription'>ContainerDescription</a>?</code></code> Hints to the container system about how the Application should be launched
</summary>





</details>

<details>
<summary>
<code>environment</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code> Additional environment variables to be added in the environment
</summary>





</details>

<details>
<summary>
<code>allowAdditionalMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for additional file mounts (default: true for interactive apps)
</summary>





</details>

<details>
<summary>
<code>allowAdditionalPeers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for connecting Jobs together (default: true)
</summary>





</details>

<details>
<summary>
<code>allowMultiNode</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable multiple replicas of this Application (default: false)
</summary>





</details>

<details>
<summary>
<code>allowPublicIp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for public IP (default false)
</summary>





</details>

<details>
<summary>
<code>allowPublicLink</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable support for public link (default: true for web apps)
</summary>





</details>

<details>
<summary>
<code>fileExtensions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> The file extensions which this Application can handle
</summary>



This list used as a suffix filter. As a result, this list should typically include the dot.


</details>

<details>
<summary>
<code>licenseServers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code> Hint used by the frontend to find appropriate license servers
</summary>





</details>

<details>
<summary>
<code>shouldAllowAdditionalMounts</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>shouldAllowAdditionalPeers</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `ApplicationType`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The ApplicationType determines how user's interact with an Application_

```kotlin
enum class ApplicationType {
    BATCH,
    VNC,
    WEB,
}
```
- `BATCH`: A non-interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  which runs without user input
- `VNC`: An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a remote desktop interface
- `WEB`: An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a graphical web interface

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BATCH</code> A non-interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  which runs without user input
</summary>





</details>

<details>
<summary>
<code>VNC</code> An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a remote desktop interface
</summary>





</details>

<details>
<summary>
<code>WEB</code> An interactive [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  exposing a graphical web interface
</summary>





</details>



</details>



---

### `VncDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Information to the Provider about how to reach the VNC services_

```kotlin
data class VncDescription(
    val password: String?,
    val port: Int?,
)
```
Providers must use this information when 
[opening an interactive session](/docs/reference/jobs.openInteractiveSession.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>password</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>port</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `WebDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Information to the Provider about how to reach the web services_

```kotlin
data class WebDescription(
    val port: Int?,
)
```
Providers must use this information when 
[opening an interactive session](/docs/reference/jobs.openInteractiveSession.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>port</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code>
</summary>





</details>



</details>



---

### `ContainerDescription`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Information to the Provider about how to launch the container_

```kotlin
data class ContainerDescription(
    val changeWorkingDirectory: Boolean?,
    val runAsRoot: Boolean?,
    val runAsRealUser: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>changeWorkingDirectory</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>runAsRoot</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>runAsRealUser</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `ApplicationParameter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An ApplicationParameter describe a single input parameter to an Application._

```kotlin
sealed class ApplicationParameter {
    abstract val defaultValue: Any?
    abstract val description: String
    abstract val name: String
    abstract val optional: Boolean
    abstract val title: String?

    class InputFile : ApplicationParameter()
    class InputDirectory : ApplicationParameter()
    class Text : ApplicationParameter()
    class TextArea : ApplicationParameter()
    class Integer : ApplicationParameter()
    class FloatingPoint : ApplicationParameter()
    class Bool : ApplicationParameter()
    class Enumeration : ApplicationParameter()
    class Peer : ApplicationParameter()
    class Ingress : ApplicationParameter()
    class LicenseServer : ApplicationParameter()
    class NetworkIP : ApplicationParameter()
}
```
All [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md)s contain metadata used for the presentation in the frontend. This metadata 
includes a title and help-text. This allows UCloud to create a rich user-interface with widgets which are easy to 
use. 

When the user requests the creation of a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), they supply a lot of 
information. This includes a reference to the [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  and a set of [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md)s. 
The user must supply a value for every mandatory [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md). Every parameter has a type 
associated with it. This type controls the set of valid [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md)s it can take.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `ApplicationParameter.TextArea`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An ApplicationParameter describe a single input parameter to an Application._

```kotlin
data class TextArea(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val type: String /* "textarea" */,
)
```
All [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md)s contain metadata used for the presentation in the frontend. This metadata 
includes a title and help-text. This allows UCloud to create a rich user-interface with widgets which are easy to 
use. 

When the user requests the creation of a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), they supply a lot of 
information. This includes a reference to the [`Application`](/docs/reference/dk.sdu.cloud.app.store.api.Application.md)  and a set of [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md)s. 
The user must supply a value for every mandatory [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md). Every parameter has a type 
associated with it. This type controls the set of valid [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md)s it can take.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "textarea" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.Bool`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts any boolean value_

```kotlin
data class Bool(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val trueValue: String?,
    val falseValue: String?,
    val type: String /* "boolean" */,
)
```
__Compatible with:__ [`AppParameterValue.Bool`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Bool.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>trueValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>falseValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "boolean" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.Enumeration`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts an enum_

```kotlin
data class Enumeration(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val options: List<ApplicationParameter.EnumOption>?,
    val type: String /* "enumeration" */,
)
```
__Compatible with:__ [`AppParameterValue.Text`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Text.md)  (Note: the text should match the `value` of the selected 
option)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>options</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationparameter.enumoption'>ApplicationParameter.EnumOption</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "enumeration" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.FloatingPoint`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts any floating point value_

```kotlin
data class FloatingPoint(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val min: Double?,
    val max: Double?,
    val step: Double?,
    val unitName: String?,
    val type: String /* "floating_point" */,
)
```
__Compatible with:__ [`AppParameterValue.FloatingPoint`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.FloatingPoint.md) 

This parameter can be tweaked using the various options. For example, it is possible to provide a minimum
and maximum value.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>min</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>max</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>step</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "floating_point" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.Ingress`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts a ingress (public link)_

```kotlin
data class Ingress(
    val name: String?,
    val title: String?,
    val description: String?,
    val defaultValue: Any?,
    val optional: Boolean,
    val type: String /* "ingress" */,
)
```
__Compatible with:__ [`AppParameterValue.Ingress`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Ingress.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "ingress" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.InputDirectory`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts UFiles of type `DIRECTORY`_

```kotlin
data class InputDirectory(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val type: String /* "input_directory" */,
)
```
__Compatible with:__ [`AppParameterValue.File`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.File.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "input_directory" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.InputFile`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts UFiles of type `FILE`_

```kotlin
data class InputFile(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val type: String /* "input_file" */,
)
```
__Compatible with:__ [`AppParameterValue.File`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.File.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "input_file" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.Integer`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts any integer value_

```kotlin
data class Integer(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val min: Long?,
    val max: Long?,
    val step: Long?,
    val unitName: String?,
    val type: String /* "integer" */,
)
```
__Compatible with:__ [`AppParameterValue.Integer`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Integer.md) 

This parameter can be tweaked using the various options. For example, it is possible to provide a minimum
and maximum value.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>min</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>max</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>step</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>unitName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "integer" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.LicenseServer`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts a license_

```kotlin
data class LicenseServer(
    val name: String?,
    val title: String?,
    val optional: Boolean?,
    val description: String?,
    val tagged: List<String>,
    val defaultValue: Any?,
    val type: String /* "license_server" */,
)
```
__Compatible with:__ [`AppParameterValue.License`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.License.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>tagged</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "license_server" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.NetworkIP`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts an IP address_

```kotlin
data class NetworkIP(
    val name: String?,
    val title: String?,
    val description: String?,
    val defaultValue: Any?,
    val optional: Boolean,
    val type: String /* "network_ip" */,
)
```
__Compatible with:__ [`AppParameterValue.Network`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Network.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "network_ip" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.Peer`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts a peering Job_

```kotlin
data class Peer(
    val name: String?,
    val title: String?,
    val description: String,
    val suggestedApplication: String?,
    val defaultValue: Any?,
    val optional: Boolean,
    val type: String /* "peer" */,
)
```
__Compatible with:__ [`AppParameterValue.Peer`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Peer.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>suggestedApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "peer" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationParameter.Text`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts text_

```kotlin
data class Text(
    val name: String?,
    val optional: Boolean?,
    val defaultValue: Any?,
    val title: String?,
    val description: String?,
    val type: String /* "text" */,
)
```
__Compatible with:__ [`AppParameterValue.Text`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Text.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "text" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An `AppParameterValue` is value which is supplied to a parameter of an `Application`._

```kotlin
sealed class AppParameterValue {
    class File : AppParameterValue()
    class Bool : AppParameterValue()
    class TextArea : AppParameterValue()
    class Text : AppParameterValue()
    class Integer : AppParameterValue()
    class FloatingPoint : AppParameterValue()
    class Peer : AppParameterValue()
    class License : AppParameterValue()
    class BlockStorage : AppParameterValue()
    class Network : AppParameterValue()
    class Ingress : AppParameterValue()
}
```
Each value type can is type-compatible with one or more `ApplicationParameter`s. The effect of a specific value depends
on its use-site, and the type of its associated parameter.

`ApplicationParameter`s have the following usage sites:

- Invocation: This affects the command line arguments passed to the software.
- Environment variables: This affects the environment variables passed to the software.
- Resources: This only affects the resources which are imported into the software environment. Not all values can be
  used as a resource.



---

### `AppParameterValue.BlockStorage`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to block storage (Not yet implemented)_

```kotlin
data class BlockStorage(
    val id: String,
    val type: String /* "block_storage" */,
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
<code>type</code>: <code><code>String /* "block_storage" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.Bool`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A boolean value (true or false)_

```kotlin
data class Bool(
    val value: Boolean,
    val type: String /* "boolean" */,
)
```
- __Compatible with:__ `ApplicationParameter.Bool`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ `trueValue` of `ApplicationParameter.Bool` if value is `true` otherwise `falseValue`
- __Side effects:__ None

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "boolean" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.File`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to a UCloud file_

```kotlin
data class File(
    val path: String,
    val readOnly: Boolean?,
    val type: String /* "file" */,
)
```
- __Compatible with:__ `ApplicationParameter.InputFile` and `ApplicationParameter.InputDirectory`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ The absolute path to the file or directory in the software's environment
- __Side effects:__ Includes the file or directory in the `Job`'s temporary work directory
    
The path of the file must be absolute and refers to either a UCloud directory or file.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The absolute path to the file or directory in UCloud
</summary>





</details>

<details>
<summary>
<code>readOnly</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Indicates if this file or directory should be mounted as read-only
</summary>



A provider must reject the request if it does not support read-only mounts when `readOnly = true`.


</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "file" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.FloatingPoint`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A floating point value_

```kotlin
data class FloatingPoint(
    val value: Double,
    val type: String /* "floating_point" */,
)
```
- __Compatible with:__ `ApplicationParameter.FloatingPoint`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The number
- __Side effects:__ None

Internally this uses a big decimal type and there are no defined limits.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/'>Double</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "floating_point" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.Ingress`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to an HTTP ingress, registered locally at the provider_

```kotlin
data class Ingress(
    val id: String,
    val type: String /* "ingress" */,
)
```
- __Compatible with:__ `ApplicationParameter.Ingress`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ `${id}`
- __Side effects:__ Configures an HTTP ingress for the application's interactive web interface. This interface should
  not perform any validation, that is, the application should be publicly accessible.

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
<code>type</code>: <code><code>String /* "ingress" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.Integer`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An integral value_

```kotlin
data class Integer(
    val value: Long,
    val type: String /* "integer" */,
)
```
- __Compatible with:__ `ApplicationParameter.Integer`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The number
- __Side effects:__ None

Internally this uses a big integer type and there are no defined limits.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "integer" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.License`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to a software license, registered locally at the provider_

```kotlin
data class License(
    val id: String,
    val type: String /* "license_server" */,
)
```
- __Compatible with:__ `ApplicationParameter.LicenseServer`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ `${license.address}:${license.port}/${license.key}` or 
  `${license.address}:${license.port}` if no key is provided
- __Side effects:__ None

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
<code>type</code>: <code><code>String /* "license_server" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.Network`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to block storage (Not yet implemented)_

```kotlin
data class Network(
    val id: String,
    val type: String /* "network" */,
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
<code>type</code>: <code><code>String /* "network" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.Peer`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to a separate UCloud `Job`_

```kotlin
data class Peer(
    val hostname: String,
    val jobId: String,
    val type: String /* "peer" */,
)
```
- __Compatible with:__ `ApplicationParameter.Peer`
- __Mountable as a resource:__ ✅ Yes
- __Expands to:__ The `hostname`
- __Side effects:__ Configures the firewall to allow bidirectional communication between this `Job` and the peering 
  `Job`

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>hostname</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>jobId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "peer" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AppParameterValue.Text`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A textual value_

```kotlin
data class Text(
    val value: String,
    val type: String /* "text" */,
)
```
- __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "text" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `InvocationParameter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_InvocationParameters supply values to either the command-line or environment variables._

```kotlin
sealed class InvocationParameter {
    class EnvironmentVariableParameter : InvocationParameter()
    class WordInvocationParameter : InvocationParameter()
    class VariableInvocationParameter : InvocationParameter()
    class BooleanFlagParameter : InvocationParameter()
}
```
Every parameter can run in one of two contexts. They produce a value when combined with a [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md)  
and a [`AppParameterValue`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.md):

- __Command line argument:__ Produces zero or more arguments for the command-line
- __Environment variable:__ Produces exactly one value.

For each of the [`InvocationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.InvocationParameter.md)  types, we will describe the value(s) they produce. We will also highlight 
notable differences between CLI args and environment variables.



---

### `BooleanFlagParameter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Produces a toggleable command-line flag_

```kotlin
data class BooleanFlagParameter(
    val variableName: String,
    val flag: String,
    val type: String /* "bool_flag" */,
)
```
The parameter referenced by `variableName` must be of type [`ApplicationParameter.Bool`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.Bool.md), and the value
must be [`AppParamValue.Bool`](/docs/reference/dk.sdu.cloud.app.store.api.AppParamValue.Bool.md). This invocation parameter will produce the `flag` if the variable's value is
`true`. Otherwise, it will produce no values.

__Example:__ Example (with true value)

_`VariableInvocationParameter`:_

```json
{
    "type": "bool_flag",
    "variableName": ["myVariable"],
    "flag": "--example"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "bool", "value": true }
}
```

_Expands to:_

```bash
"--example"
```

__Example:__ Example (with false value)

_`VariableInvocationParameter`:_

```json
{
    "type": "bool_flag",
    "variableName": ["myVariable"],
    "flag": "--example"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "bool", "value": false }
}
```

_Expands to (nothing):_

```bash

```

__Example:__ With spaces

_`VariableInvocationParameter`:_

```json
{
    "type": "bool_flag",
    "variableName": ["myVariable"],
    "flag": "--hello world"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "bool", "value": true }
}
```

_Expands to:_

```bash
"--hello world"
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>variableName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>flag</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "bool_flag" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `EnvironmentVariableParameter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Produces an environment variable (TODO Documentation)_

```kotlin
data class EnvironmentVariableParameter(
    val variable: String,
    val type: String /* "env" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>variable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "env" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `VariableInvocationParameter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An InvocationParameter which produces value(s) from parameters._

```kotlin
data class VariableInvocationParameter(
    val variableNames: List<String>,
    val prefixGlobal: String?,
    val suffixGlobal: String?,
    val prefixVariable: String?,
    val suffixVariable: String?,
    val isPrefixVariablePartOfArg: Boolean?,
    val isSuffixVariablePartOfArg: Boolean?,
    val type: String /* "var" */,
)
```
The parameter receives a list of `variableNames`. Each must reference an [`ApplicationParameter`](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md). It is 
valid to reference both optional and mandatory parameters. This invocation will produce zero values if all the 
parameters have no value. This is regardless of the prefixes and suffixes.

The invocation accepts prefixes and suffixes. These will alter the values produced. The global affixes always 
produce one value each, if supplied. The variable specific affixes produce their own value if 
`isXVariablePartOfArg`.

__Example:__ Simple variable

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable"]
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "text", "value": "Hello, World!" }
}
```

_Expands to:_

```bash
"Hello, World!"
```

__Example:__ Global prefix (command line flags)

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable"],
    "prefixGlobal": "--count"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 }
}
```

_Expands to:_

```bash
"--count" "42"
```

__Example:__ Multiple variables

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable", "mySecondVariable"],
    "prefixGlobal": "--count"
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 },
    "mySecondVariable": { "type": "integer", "value": 120 },
}
```

_Expands to:_

```bash
"--count" "42" "120"
```

__Example:__ Variable prefixes and suffixes

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable"],
    "prefixGlobal": "--entries",
    "prefixVariable": "--entry",
    "suffixVariable": "--next",
    "isPrefixVariablePartOfArg": true,
    "isSuffixVariablePartOfArg": false
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 },
}
```

_Expands to:_

```bash
"--entries" "--entry42" "--next"
```

__Example:__ Complete example

_`VariableInvocationParameter`:_

```json
{
    "variableNames": ["myVariable", "mySecondVariable"],
    "prefixGlobal": "--entries",
    "prefixVariable": "--entry",
    "suffixVariable": "--next",
    "suffixGlobal": "--endOfEntries",
    "isPrefixVariablePartOfArg": false,
    "isSuffixVariablePartOfArg": true
}
```

_Values (`AppParameterValue`):_

```json
{
    "myVariable": { "type": "integer", "value": 42 },
    "mySecondVariable": { "type": "text", "value": "hello" },
}
```

_Expands to:_

```bash
"--entries" "--entry" "42--next" "--entry" "hello--next" "--endOfEntries"
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>variableNames</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>prefixGlobal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>suffixGlobal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>prefixVariable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>suffixVariable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isPrefixVariablePartOfArg</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>isSuffixVariablePartOfArg</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "var" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `WordInvocationParameter`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A static value for an InvocationParameter_

```kotlin
data class WordInvocationParameter(
    val word: String,
    val type: String /* "word" */,
)
```
This value is static and will always produce only a single value. As a result, you do not need to escape any values
for this parameter.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>word</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "word" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `AccessEntity`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AccessEntity(
    val user: String?,
    val project: String?,
    val group: String?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>user</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>project</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>group</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>



</details>



---

### `AppParameterValue.TextArea`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A textual value_

```kotlin
data class TextArea(
    val value: String,
    val type: String /* "textarea" */,
)
```
- __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "textarea" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `ApplicationAccessRight`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ApplicationAccessRight {
    LAUNCH,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>LAUNCH</code>
</summary>





</details>



</details>



---

### `ApplicationParameter.EnumOption`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class EnumOption(
    val name: String,
    val value: String,
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
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ApplicationSummaryWithFavorite`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Applications specify the input parameters and invocation of a software package._

```kotlin
data class ApplicationSummaryWithFavorite(
    val metadata: ApplicationMetadata,
    val favorite: Boolean,
    val tags: List<String>,
)
```
For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#applicationmetadata'>ApplicationMetadata</a></code></code>
</summary>





</details>

<details>
<summary>
<code>favorite</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ApplicationWithExtension`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Applications specify the input parameters and invocation of a software package._

```kotlin
data class ApplicationWithExtension(
    val metadata: ApplicationMetadata,
    val extensions: List<String>,
)
```
For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#applicationmetadata'>ApplicationMetadata</a></code></code>
</summary>





</details>

<details>
<summary>
<code>extensions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ApplicationWithFavoriteAndTags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Applications specify the input parameters and invocation of a software package._

```kotlin
data class ApplicationWithFavoriteAndTags(
    val metadata: ApplicationMetadata,
    val invocation: ApplicationInvocationDescription,
    val favorite: Boolean,
    val tags: List<String>,
)
```
For more information see the [full documentation](/docs/developer-guide/orchestration/compute/appstore/apps.md).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#applicationmetadata'>ApplicationMetadata</a></code></code>
</summary>





</details>

<details>
<summary>
<code>invocation</code>: <code><code><a href='#applicationinvocationdescription'>ApplicationInvocationDescription</a></code></code>
</summary>





</details>

<details>
<summary>
<code>favorite</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `DetailedAccessEntity`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DetailedAccessEntity(
    val user: String?,
    val project: Project?,
    val group: Project?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>user</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>project</code>: <code><code><a href='#project'>Project</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>group</code>: <code><code><a href='#project'>Project</a>?</code></code>
</summary>





</details>



</details>



---

### `DetailedEntityWithPermission`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DetailedEntityWithPermission(
    val entity: DetailedAccessEntity,
    val permission: ApplicationAccessRight,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>entity</code>: <code><code><a href='#detailedaccessentity'>DetailedAccessEntity</a></code></code>
</summary>





</details>

<details>
<summary>
<code>permission</code>: <code><code><a href='#applicationaccessright'>ApplicationAccessRight</a></code></code>
</summary>





</details>



</details>



---

### `FindApplicationAndOptionalDependencies`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindApplicationAndOptionalDependencies(
    val appName: String,
    val appVersion: String,
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
<code>appVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FindBySupportedFileExtension`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class FindBySupportedFileExtension(
    val files: List<String>,
    val itemsPerPage: Int?,
    val next: String?,
    val consistency: PaginationRequestV2Consistency?,
    val itemsToSkip: Long?,
)
```
Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
semantics of the call:

| Consistency | Description |
|-------------|-------------|
| `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
| `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |

The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
results to be consistent if it contains a complete view at some point in time. In practice this means that the results
must contain all the items, in the correct order and without duplicates.

If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
consistent.

The results might become inconsistent if the client either takes too long, or a service instance goes down while
fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.

---

__📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
paginate through the results.

---

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>files</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>itemsPerPage</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a>?</code></code> Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
</summary>





</details>

<details>
<summary>
<code>next</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A token requesting the next page of items
</summary>





</details>

<details>
<summary>
<code>consistency</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.PaginationRequestV2Consistency.md'>PaginationRequestV2Consistency</a>?</code></code> Controls the consistency guarantees provided by the backend
</summary>





</details>

<details>
<summary>
<code>itemsToSkip</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> Items to skip ahead
</summary>





</details>



</details>



---

### `Project`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Project(
    val id: String,
    val title: String,
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
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `ToolReference`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A reference to a Tool_

```kotlin
data class ToolReference(
    val name: String,
    val version: String,
    val tool: Tool?,
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

<details>
<summary>
<code>tool</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.Tool.md'>Tool</a>?</code></code>
</summary>





</details>



</details>



---

### `ACLEntryRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ACLEntryRequest(
    val entity: AccessEntity,
    val rights: ApplicationAccessRight,
    val revoke: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>entity</code>: <code><code><a href='#accessentity'>AccessEntity</a></code></code>
</summary>





</details>

<details>
<summary>
<code>rights</code>: <code><code><a href='#applicationaccessright'>ApplicationAccessRight</a></code></code>
</summary>





</details>

<details>
<summary>
<code>revoke</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>



</details>



---

### `AdvancedSearchRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AdvancedSearchRequest(
    val query: String?,
    val tags: List<String>?,
    val showAllVersions: Boolean,
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
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>showAllVersions</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
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

### `AppSearchRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class AppSearchRequest(
    val query: String,
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
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
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

### `CreateTagsRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class CreateTagsRequest(
    val tags: List<String>,
    val applicationName: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>tags</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>

<details>
<summary>
<code>applicationName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `DeleteAppRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class DeleteAppRequest(
    val appName: String,
    val appVersion: String,
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
<code>appVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FavoriteRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FavoriteRequest(
    val appName: String,
    val appVersion: String,
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
<code>appVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FindLatestByToolRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindLatestByToolRequest(
    val tool: String,
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
<code>tool</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
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

### `HasPermissionRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class HasPermissionRequest(
    val appName: String,
    val appVersion: String,
    val permission: List<ApplicationAccessRight>,
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
<code>appVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>permission</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#applicationaccessright'>ApplicationAccessRight</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `IsPublicRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IsPublicRequest(
    val applications: List<NameAndVersion>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applications</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.store.api.NameAndVersion.md'>NameAndVersion</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `ListAclRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ListAclRequest(
    val appName: String,
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



</details>



---

### `SetPublicRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class SetPublicRequest(
    val appName: String,
    val appVersion: String,
    val public: Boolean,
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
<code>appVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>public</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>



</details>



---

### `TagSearchRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class TagSearchRequest(
    val query: String,
    val excludeTools: String?,
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
<code>query</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>excludeTools</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
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

### `UpdateAclRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class UpdateAclRequest(
    val applicationName: String,
    val changes: List<ACLEntryRequest>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>applicationName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>changes</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#aclentryrequest'>ACLEntryRequest</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `IsPublicResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class IsPublicResponse(
    val public: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>public</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

