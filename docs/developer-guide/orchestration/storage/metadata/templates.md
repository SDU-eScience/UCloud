<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/shares.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/metadata/documents.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Metadata](/docs/developer-guide/orchestration/storage/metadata/README.md) / Metadata Templates
# Metadata Templates

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Metadata templates define the schema for metadata documents._

## Rationale

__📝 NOTE:__ This API follows the standard Resources API. We recommend that you have already read and understood the
concepts described [here](/docs/developer-guide/orchestration/resources.md).
        
---

    

UCloud supports arbitrary of files. This feature is useful for general data management. It allows users to 
tag documents at a glance and search through them.

This feature consists of two parts:

1. __Metadata templates (you are here):__ Templates specify the schema. You can think of this as a way of 
   defining _how_ your documents should look. We use them to generate user interfaces and visual 
   representations of your documents.
2. __Metadata documents (next section):__ Documents fill out the values of a template. When you create a 
   document you must attach it to a file also.

At a technical level, we implement metadata templates using [JSON schema](https://json-schema.org/). 
This gives you a fair amount of flexibility to control the format of documents. Of course, not everything 
is machine-checkable. To mitigate this, templates can require that changes go through an approval process.
Only administrators of a workspace can approve such changes.

## Table of Contents
<details>
<summary>
<a href='#example-the-sensitivity-template'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-the-sensitivity-template'>The Sensitivity Template</a></td></tr>
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
<td>Browses the catalog of available resources</td>
</tr>
<tr>
<td><a href='#browsetemplates'><code>browseTemplates</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieve'><code>retrieve</code></a></td>
<td>Retrieve a single resource</td>
</tr>
<tr>
<td><a href='#retrievelatest'><code>retrieveLatest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#retrieveproducts'><code>retrieveProducts</code></a></td>
<td>Retrieve product support for all accessible providers</td>
</tr>
<tr>
<td><a href='#retrievetemplate'><code>retrieveTemplate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td>Creates one or more resources</td>
</tr>
<tr>
<td><a href='#createtemplate'><code>createTemplate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#deprecate'><code>deprecate</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#init'><code>init</code></a></td>
<td>Request (potential) initialization of resources</td>
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
<td><a href='#filemetadatatemplateandversion'><code>FileMetadataTemplateAndVersion</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatatemplatenamespace'><code>FileMetadataTemplateNamespace</code></a></td>
<td>A `Resource` is the core data model used to synchronize tasks between UCloud and Provider.</td>
</tr>
<tr>
<td><a href='#filemetadatatemplatenamespace.spec'><code>FileMetadataTemplateNamespace.Spec</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatatemplatenamespace.status'><code>FileMetadataTemplateNamespace.Status</code></a></td>
<td>Describes the current state of the `Resource`</td>
</tr>
<tr>
<td><a href='#filemetadatatemplatenamespace.update'><code>FileMetadataTemplateNamespace.Update</code></a></td>
<td>Describes an update to the `Resource`</td>
</tr>
<tr>
<td><a href='#filemetadatatemplatenamespaceflags'><code>FileMetadataTemplateNamespaceFlags</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatatemplatesupport'><code>FileMetadataTemplateSupport</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatatemplatesbrowsetemplatesrequest'><code>FileMetadataTemplatesBrowseTemplatesRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
</tbody></table>


</details>

## Example: The Sensitivity Template
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
FileMetadataTemplateNamespaces.createTemplate.call(
    bulkRequestOf(FileMetadataTemplate(
        changeLog = "Initial version", 
        createdAt = 0, 
        description = "File sensitivity for files", 
        inheritable = true, 
        namespaceId = "sensitivity", 
        namespaceName = null, 
        namespaceType = FileMetadataTemplateNamespaceType.COLLABORATORS, 
        requireApproval = true, 
        schema = JsonObject(mapOf("type" to JsonLiteral(
            content = "object", 
            isString = true, 
        )),"title" to JsonLiteral(
            content = "UCloud File Sensitivity", 
            isString = true, 
        )),"required" to listOf(JsonLiteral(
            content = "sensitivity", 
            isString = true, 
        ))),"properties" to JsonObject(mapOf("sensitivity" to JsonObject(mapOf("enum" to listOf(JsonLiteral(
            content = "SENSITIVE", 
            isString = true, 
        ), JsonLiteral(
            content = "CONFIDENTIAL", 
            isString = true, 
        ), JsonLiteral(
            content = "PRIVATE", 
            isString = true, 
        ))),"type" to JsonLiteral(
            content = "string", 
            isString = true, 
        )),"title" to JsonLiteral(
            content = "File Sensitivity", 
            isString = true, 
        )),"enumNames" to listOf(JsonLiteral(
            content = "Sensitive", 
            isString = true, 
        ), JsonLiteral(
            content = "Confidential", 
            isString = true, 
        ), JsonLiteral(
            content = "Private", 
            isString = true, 
        ))),))),))),"dependencies" to JsonObject(mapOf())),)), 
        title = "Sensitivity", 
        uiSchema = JsonObject(mapOf("ui:order" to listOf(JsonLiteral(
            content = "sensitivity", 
            isString = true, 
        ))),)), 
        version = "1.0.0", 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FileMetadataTemplateAndVersion(
        id = "15123", 
        version = "1.0.0", 
    )), 
)
*/
FileMetadataTemplateNamespaces.retrieveLatest.call(
    FindByStringId(
        id = "15123", 
    ),
    user
).orThrow()

/*
FileMetadataTemplate(
    changeLog = "Initial version", 
    createdAt = 0, 
    description = "File sensitivity for files", 
    inheritable = true, 
    namespaceId = "sensitivity", 
    namespaceName = null, 
    namespaceType = FileMetadataTemplateNamespaceType.COLLABORATORS, 
    requireApproval = true, 
    schema = JsonObject(mapOf("type" to JsonLiteral(
        content = "object", 
        isString = true, 
    )),"title" to JsonLiteral(
        content = "UCloud File Sensitivity", 
        isString = true, 
    )),"required" to listOf(JsonLiteral(
        content = "sensitivity", 
        isString = true, 
    ))),"properties" to JsonObject(mapOf("sensitivity" to JsonObject(mapOf("enum" to listOf(JsonLiteral(
        content = "SENSITIVE", 
        isString = true, 
    ), JsonLiteral(
        content = "CONFIDENTIAL", 
        isString = true, 
    ), JsonLiteral(
        content = "PRIVATE", 
        isString = true, 
    ))),"type" to JsonLiteral(
        content = "string", 
        isString = true, 
    )),"title" to JsonLiteral(
        content = "File Sensitivity", 
        isString = true, 
    )),"enumNames" to listOf(JsonLiteral(
        content = "Sensitive", 
        isString = true, 
    ), JsonLiteral(
        content = "Confidential", 
        isString = true, 
    ), JsonLiteral(
        content = "Private", 
        isString = true, 
    ))),))),))),"dependencies" to JsonObject(mapOf())),)), 
    title = "Sensitivity", 
    uiSchema = JsonObject(mapOf("ui:order" to listOf(JsonLiteral(
        content = "sensitivity", 
        isString = true, 
    ))),)), 
    version = "1.0.0", 
)
*/
FileMetadataTemplateNamespaces.browseTemplates.call(
    FileMetadataTemplatesBrowseTemplatesRequest(
        consistency = null, 
        id = "15123", 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(FileMetadataTemplate(
        changeLog = "Initial version", 
        createdAt = 0, 
        description = "File sensitivity for files", 
        inheritable = true, 
        namespaceId = "sensitivity", 
        namespaceName = null, 
        namespaceType = FileMetadataTemplateNamespaceType.COLLABORATORS, 
        requireApproval = true, 
        schema = JsonObject(mapOf("type" to JsonLiteral(
            content = "object", 
            isString = true, 
        )),"title" to JsonLiteral(
            content = "UCloud File Sensitivity", 
            isString = true, 
        )),"required" to listOf(JsonLiteral(
            content = "sensitivity", 
            isString = true, 
        ))),"properties" to JsonObject(mapOf("sensitivity" to JsonObject(mapOf("enum" to listOf(JsonLiteral(
            content = "SENSITIVE", 
            isString = true, 
        ), JsonLiteral(
            content = "CONFIDENTIAL", 
            isString = true, 
        ), JsonLiteral(
            content = "PRIVATE", 
            isString = true, 
        ))),"type" to JsonLiteral(
            content = "string", 
            isString = true, 
        )),"title" to JsonLiteral(
            content = "File Sensitivity", 
            isString = true, 
        )),"enumNames" to listOf(JsonLiteral(
            content = "Sensitive", 
            isString = true, 
        ), JsonLiteral(
            content = "Confidential", 
            isString = true, 
        ), JsonLiteral(
            content = "Private", 
            isString = true, 
        ))),))),))),"dependencies" to JsonObject(mapOf())),)), 
        title = "Sensitivity", 
        uiSchema = JsonObject(mapOf("ui:order" to listOf(JsonLiteral(
            content = "sensitivity", 
            isString = true, 
        ))),)), 
        version = "1.0.0", 
    )), 
    itemsPerPage = 50, 
    next = null, 
)
*/
FileMetadataTemplateNamespaces.browse.call(
    ResourceBrowseRequest(
        consistency = null, 
        flags = FileMetadataTemplateNamespaceFlags(
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterIds = null, 
            filterName = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeOthers = false, 
            includeProduct = false, 
            includeSupport = false, 
            includeUpdates = false, 
        ), 
        itemsPerPage = null, 
        itemsToSkip = null, 
        next = null, 
        sortBy = null, 
        sortDirection = SortDirection.ascending, 
    ),
    user
).orThrow()

/*
PageV2(
    items = listOf(FileMetadataTemplateNamespace(
        createdAt = 1635151675465, 
        id = "15123", 
        owner = ResourceOwner(
            createdBy = "user", 
            project = null, 
        ), 
        permissions = ResourcePermissions(
            myself = listOf(Permission.ADMIN), 
            others = emptyList(), 
        ), 
        specification = FileMetadataTemplateNamespace.Spec(
            name = "sensitivity", 
            namespaceType = FileMetadataTemplateNamespaceType.COLLABORATORS, 
            product = ProductReference(
                category = "", 
                id = "", 
                provider = "ucloud_core", 
            ), 
        ), 
        status = FileMetadataTemplateNamespace.Status(
            latestTitle = "Sensitivity", 
            resolvedProduct = null, 
            resolvedSupport = null, 
        ), 
        updates = emptyList(), 
        providerGeneratedId = "15123", 
    )), 
    itemsPerPage = 50, 
    next = null, 
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
curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/metadataTemplates/templates" -d '{
    "items": [
        {
            "namespaceId": "sensitivity",
            "title": "Sensitivity",
            "version": "1.0.0",
            "schema": {
                "type": "object",
                "title": "UCloud File Sensitivity",
                "required": [
                    "sensitivity"
                ],
                "properties": {
                    "sensitivity": {
                        "enum": [
                            "SENSITIVE",
                            "CONFIDENTIAL",
                            "PRIVATE"
                        ],
                        "type": "string",
                        "title": "File Sensitivity",
                        "enumNames": [
                            "Sensitive",
                            "Confidential",
                            "Private"
                        ]
                    }
                },
                "dependencies": {
                }
            },
            "inheritable": true,
            "requireApproval": true,
            "description": "File sensitivity for files",
            "changeLog": "Initial version",
            "namespaceType": "COLLABORATORS",
            "uiSchema": {
                "ui:order": [
                    "sensitivity"
                ]
            },
            "namespaceName": null,
            "createdAt": 0
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "15123",
#             "version": "1.0.0"
#         }
#     ]
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/metadataTemplates/retrieveLatest?id=15123" 

# {
#     "namespaceId": "sensitivity",
#     "title": "Sensitivity",
#     "version": "1.0.0",
#     "schema": {
#         "type": "object",
#         "title": "UCloud File Sensitivity",
#         "required": [
#             "sensitivity"
#         ],
#         "properties": {
#             "sensitivity": {
#                 "enum": [
#                     "SENSITIVE",
#                     "CONFIDENTIAL",
#                     "PRIVATE"
#                 ],
#                 "type": "string",
#                 "title": "File Sensitivity",
#                 "enumNames": [
#                     "Sensitive",
#                     "Confidential",
#                     "Private"
#                 ]
#             }
#         },
#         "dependencies": {
#         }
#     },
#     "inheritable": true,
#     "requireApproval": true,
#     "description": "File sensitivity for files",
#     "changeLog": "Initial version",
#     "namespaceType": "COLLABORATORS",
#     "uiSchema": {
#         "ui:order": [
#             "sensitivity"
#         ]
#     },
#     "namespaceName": null,
#     "createdAt": 0
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/metadataTemplates/browseTemplates?id=15123" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "namespaceId": "sensitivity",
#             "title": "Sensitivity",
#             "version": "1.0.0",
#             "schema": {
#                 "type": "object",
#                 "title": "UCloud File Sensitivity",
#                 "required": [
#                     "sensitivity"
#                 ],
#                 "properties": {
#                     "sensitivity": {
#                         "enum": [
#                             "SENSITIVE",
#                             "CONFIDENTIAL",
#                             "PRIVATE"
#                         ],
#                         "type": "string",
#                         "title": "File Sensitivity",
#                         "enumNames": [
#                             "Sensitive",
#                             "Confidential",
#                             "Private"
#                         ]
#                     }
#                 },
#                 "dependencies": {
#                 }
#             },
#             "inheritable": true,
#             "requireApproval": true,
#             "description": "File sensitivity for files",
#             "changeLog": "Initial version",
#             "namespaceType": "COLLABORATORS",
#             "uiSchema": {
#                 "ui:order": [
#                     "sensitivity"
#                 ]
#             },
#             "namespaceName": null,
#             "createdAt": 0
#         }
#     ],
#     "next": null
# }

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/metadataTemplates/browse?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&sortDirection=ascending" 

# {
#     "itemsPerPage": 50,
#     "items": [
#         {
#             "id": "15123",
#             "specification": {
#                 "name": "sensitivity",
#                 "namespaceType": "COLLABORATORS",
#                 "product": {
#                     "id": "",
#                     "category": "",
#                     "provider": "ucloud_core"
#                 }
#             },
#             "createdAt": 1635151675465,
#             "status": {
#                 "latestTitle": "Sensitivity",
#                 "resolvedSupport": null,
#                 "resolvedProduct": null
#             },
#             "updates": [
#             ],
#             "owner": {
#                 "createdBy": "user",
#                 "project": null
#             },
#             "permissions": {
#                 "myself": [
#                     "ADMIN"
#                 ],
#                 "others": [
#                 ]
#             }
#         }
#     ],
#     "next": null
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files.metadataTemplates_sensitivity.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of available resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#filemetadatatemplatenamespaceflags'>FileMetadataTemplateNamespaceFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#filemetadatatemplatenamespace'>FileMetadataTemplateNamespace</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `browseTemplates`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filemetadatatemplatesbrowsetemplatesrequest'>FileMetadataTemplatesBrowseTemplatesRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.md'>FileMetadataTemplate</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve a single resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#filemetadatatemplatenamespaceflags'>FileMetadataTemplateNamespaceFlags</a>&gt;</code>|<code><a href='#filemetadatatemplatenamespace'>FileMetadataTemplateNamespace</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveLatest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.md'>FileMetadataTemplate</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `retrieveProducts`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieve product support for all accessible providers_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.SupportByProvider.md'>SupportByProvider</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#filemetadatatemplatesupport'>FileMetadataTemplateSupport</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint will determine all providers that which the authenticated user has access to, in
the current workspace. A user has access to a product, and thus a provider, if the product is
either free or if the user has been granted credits to use the product.

See also:

- [`Product`](/docs/reference/dk.sdu.cloud.accounting.api.Product.md) 
- [Grants](/docs/developer-guide/accounting-and-projects/grants/grants.md)


### `retrieveTemplate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filemetadatatemplateandversion'>FileMetadataTemplateAndVersion</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.md'>FileMetadataTemplate</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates one or more resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filemetadatatemplatenamespace.spec'>FileMetadataTemplateNamespace.Spec</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `createTemplate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplate.md'>FileMetadataTemplate</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#filemetadatatemplateandversion'>FileMetadataTemplateAndVersion</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `deprecate`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `init`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request (potential) initialization of resources_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This request is sent by the client, if the client believes that initialization of resources 
might be needed. NOTE: This request might be sent even if initialization has already taken 
place. UCloud/Core does not check if initialization has already taken place, it simply validates
the request.


### `updateAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Updates the ACL attached to a resource_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAcl.md'>UpdatedAcl</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FileMetadataTemplateAndVersion`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataTemplateAndVersion(
    val id: String,
    val version: String,
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
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataTemplateNamespace`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Resource` is the core data model used to synchronize tasks between UCloud and Provider._

```kotlin
data class FileMetadataTemplateNamespace(
    val id: String,
    val specification: FileMetadataTemplateNamespace.Spec,
    val createdAt: Long,
    val status: FileMetadataTemplateNamespace.Status,
    val updates: List<FileMetadataTemplateNamespace.Update>,
    val owner: ResourceOwner,
    val permissions: ResourcePermissions?,
    val providerGeneratedId: String?,
)
```
For more information go [here](/docs/developer-guide/orchestration/resources.md).

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
<code>specification</code>: <code><code><a href='#filemetadatatemplatenamespace.spec'>FileMetadataTemplateNamespace.Spec</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#filemetadatatemplatenamespace.status'>FileMetadataTemplateNamespace.Status</a></code></code> Holds the current status of the `Resource`
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#filemetadatatemplatenamespace.update'>FileMetadataTemplateNamespace.Update</a>&gt;</code></code> Contains a list of updates from the provider as well as UCloud
</summary>



Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
resource.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> Contains information about the original creator of the `Resource` along with project association
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





</details>



</details>



---

### `FileMetadataTemplateNamespace.Spec`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Spec(
    val name: String,
    val namespaceType: FileMetadataTemplateNamespaceType,
    val product: ProductReference,
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
<code>namespaceType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.FileMetadataTemplateNamespaceType.md'>FileMetadataTemplateNamespaceType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataTemplateNamespace.Status`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes the current state of the `Resource`_

```kotlin
data class Status(
    val latestTitle: String?,
    val resolvedSupport: ResolvedSupport<Product, FileMetadataTemplateSupport>?,
    val resolvedProduct: Product?,
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
<code>latestTitle</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedSupport</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResolvedSupport.md'>ResolvedSupport</a>&lt;<a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>, <a href='#filemetadatatemplatesupport'>FileMetadataTemplateSupport</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedProduct</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.Product.md'>Product</a>?</code></code> The resolved product referenced by `product`.
</summary>



This attribute is not included by default unless `includeProduct` is specified.


</details>



</details>



---

### `FileMetadataTemplateNamespace.Update`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Describes an update to the `Resource`_

```kotlin
data class Update(
    val timestamp: Long,
    val status: String?,
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
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> A timestamp referencing when UCloud received this update
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A generic text message describing the current status of the `Resource`
</summary>





</details>



</details>



---

### `FileMetadataTemplateNamespaceFlags`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataTemplateNamespaceFlags(
    val includeOthers: Boolean?,
    val includeUpdates: Boolean?,
    val includeSupport: Boolean?,
    val includeProduct: Boolean?,
    val filterCreatedBy: String?,
    val filterCreatedAfter: Long?,
    val filterCreatedBefore: Long?,
    val filterProvider: String?,
    val filterProductId: String?,
    val filterProductCategory: String?,
    val filterProviderIds: String?,
    val filterIds: String?,
    val filterName: String?,
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
<code>includeProduct</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Includes `specification.resolvedProduct`
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
<code>filterName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
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

### `FileMetadataTemplateSupport`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataTemplateSupport(
    val product: ProductReference?,
    val maintenance: Maintenance?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>product</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.ProductReference.md'>ProductReference</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>maintenance</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.Maintenance.md'>Maintenance</a>?</code></code>
</summary>





</details>



</details>



---

### `FileMetadataTemplatesBrowseTemplatesRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class FileMetadataTemplatesBrowseTemplatesRequest(
    val id: String,
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
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
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

