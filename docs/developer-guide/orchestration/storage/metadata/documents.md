<p align='center'>
<a href='/docs/developer-guide/orchestration/storage/metadata/templates.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/orchestration/storage/providers/resources.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Metadata](/docs/developer-guide/orchestration/storage/metadata/README.md) / Metadata Documents
# Metadata Documents

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Metadata documents form the foundation of data management in UCloud._

## Rationale

UCloud supports arbitrary of files. This feature is useful for general data management. It allows users to 
tag documents at a glance and search through them.

This feature consists of two parts:

1. __Metadata templates (previous section):__ Templates specify the schema. You can think of this as a way of 
   defining _how_ your documents should look. We use them to generate user interfaces and visual 
   representations of your documents.
2. __Metadata documents (you are here):__ Documents fill out the values of a template. When you create a 
   document you must attach it to a file also.

## Table of Contents
<details>
<summary>
<a href='#example-sensitivity-document'>1. Examples</a>
</summary>

<table><thead><tr>
<th>Description</th>
</tr></thread>
<tbody>
<tr><td><a href='#example-sensitivity-document'>Sensitivity Document</a></td></tr>
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
<td>Browses metadata documents</td>
</tr>
<tr>
<td><a href='#retrieveall'><code>retrieveAll</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#approve'><code>approve</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#create'><code>create</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#delete'><code>delete</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#movemetadata'><code>moveMetadata</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#reject'><code>reject</code></a></td>
<td><i>No description</i></td>
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
<td><a href='#filemetadatadocument'><code>FileMetadataDocument</code></a></td>
<td>A metadata document which conforms to a `FileMetadataTemplate`</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.spec'><code>FileMetadataDocument.Spec</code></a></td>
<td>Specification of a FileMetadataDocument</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.status'><code>FileMetadataDocument.Status</code></a></td>
<td>The current status of a metadata document</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.approvalstatus'><code>FileMetadataDocument.ApprovalStatus</code></a></td>
<td>The approval status of a metadata document</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.approvalstatus.approved'><code>FileMetadataDocument.ApprovalStatus.Approved</code></a></td>
<td>The metadata change has been approved by an admin in the workspace</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.approvalstatus.pending'><code>FileMetadataDocument.ApprovalStatus.Pending</code></a></td>
<td>The metadata document has not yet been approved</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.approvalstatus.rejected'><code>FileMetadataDocument.ApprovalStatus.Rejected</code></a></td>
<td>The metadata document has been rejected by an admin of the workspace</td>
</tr>
<tr>
<td><a href='#filemetadatadocument.approvalstatus.notrequired'><code>FileMetadataDocument.ApprovalStatus.NotRequired</code></a></td>
<td>The metadata document does not require approval</td>
</tr>
<tr>
<td><a href='#filemetadataattached'><code>FileMetadataAttached</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatatemplate'><code>FileMetadataTemplate</code></a></td>
<td>A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`</td>
</tr>
<tr>
<td><a href='#filemetadatatemplatenamespacetype'><code>FileMetadataTemplateNamespaceType</code></a></td>
<td>Determines how the metadata template is namespaces</td>
</tr>
<tr>
<td><a href='#filemetadataaddrequestitem'><code>FileMetadataAddRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatabrowserequest'><code>FileMetadataBrowseRequest</code></a></td>
<td>The base type for requesting paginated content.</td>
</tr>
<tr>
<td><a href='#filemetadatadeleterequestitem'><code>FileMetadataDeleteRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadatamoverequestitem'><code>FileMetadataMoveRequestItem</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadataretrieveallrequest'><code>FileMetadataRetrieveAllRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#filemetadataretrieveallresponse'><code>FileMetadataRetrieveAllResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>

## Example: Sensitivity Document
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

/* In this example, we will show how to create a metadata document and attach it to a file. */


/* We already have a metadata template in the catalog: */

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
        coerceToInlineType = null, 
        content = "object", 
        isString = true, 
    )),"title" to JsonLiteral(
        coerceToInlineType = null, 
        content = "UCloud File Sensitivity", 
        isString = true, 
    )),"required" to listOf(JsonLiteral(
        coerceToInlineType = null, 
        content = "sensitivity", 
        isString = true, 
    ))),"properties" to JsonObject(mapOf("sensitivity" to JsonObject(mapOf("enum" to listOf(JsonLiteral(
        coerceToInlineType = null, 
        content = "SENSITIVE", 
        isString = true, 
    ), JsonLiteral(
        coerceToInlineType = null, 
        content = "CONFIDENTIAL", 
        isString = true, 
    ), JsonLiteral(
        coerceToInlineType = null, 
        content = "PRIVATE", 
        isString = true, 
    ))),"type" to JsonLiteral(
        coerceToInlineType = null, 
        content = "string", 
        isString = true, 
    )),"title" to JsonLiteral(
        coerceToInlineType = null, 
        content = "File Sensitivity", 
        isString = true, 
    )),"enumNames" to listOf(JsonLiteral(
        coerceToInlineType = null, 
        content = "Sensitive", 
        isString = true, 
    ), JsonLiteral(
        coerceToInlineType = null, 
        content = "Confidential", 
        isString = true, 
    ), JsonLiteral(
        coerceToInlineType = null, 
        content = "Private", 
        isString = true, 
    ))),))),))),"dependencies" to JsonObject(mapOf())),)), 
    title = "Sensitivity", 
    uiSchema = JsonObject(mapOf("ui:order" to listOf(JsonLiteral(
        coerceToInlineType = null, 
        content = "sensitivity", 
        isString = true, 
    ))),)), 
    version = "1.0.0", 
)
*/

/* Using this, we can create a metadata document and attach it to our file */

FileMetadata.create.call(
    bulkRequestOf(FileMetadataAddRequestItem(
        fileId = "/51231/my/file", 
        metadata = FileMetadataDocument.Spec(
            changeLog = "New sensitivity", 
            document = JsonObject(mapOf("sensitivity" to JsonLiteral(
                coerceToInlineType = null, 
                content = "SENSITIVE", 
                isString = true, 
            )),)), 
            templateId = "15123", 
            version = "1.0.0", 
        ), 
    )),
    user
).orThrow()

/*
BulkResponse(
    responses = listOf(FindByStringId(
        id = "651233", 
    )), 
)
*/

/* This specific template requires approval from a workspace admin. We can do this by calling approve. */

FileMetadata.approve.call(
    bulkRequestOf(FindByStringId(
        id = "651233", 
    )),
    user
).orThrow()

/*
Unit
*/

/* We can view the metadata by adding includeMetadata = true when requesting any file */

Files.retrieve.call(
    ResourceRetrieveRequest(
        flags = UFileIncludeFlags(
            allowUnsupportedInclude = null, 
            filterByFileExtension = null, 
            filterCreatedAfter = null, 
            filterCreatedBefore = null, 
            filterCreatedBy = null, 
            filterHiddenFiles = false, 
            filterIds = null, 
            filterProductCategory = null, 
            filterProductId = null, 
            filterProvider = null, 
            filterProviderIds = null, 
            hideProductCategory = null, 
            hideProductId = null, 
            hideProvider = null, 
            includeMetadata = true, 
            includeOthers = false, 
            includePermissions = null, 
            includeProduct = false, 
            includeSizes = null, 
            includeSupport = false, 
            includeTimestamps = null, 
            includeUnixInfo = null, 
            includeUpdates = false, 
            path = null, 
        ), 
        id = "51231", 
    ),
    user
).orThrow()

/*
UFile(
    createdAt = 1635151675465, 
    id = "/51231/my/file", 
    owner = ResourceOwner(
        createdBy = "user", 
        project = null, 
    ), 
    permissions = ResourcePermissions(
        myself = listOf(Permission.ADMIN), 
        others = emptyList(), 
    ), 
    specification = UFileSpecification(
        collection = "51231", 
        product = ProductReference(
            category = "example-ssd", 
            id = "example-ssd", 
            provider = "example", 
        ), 
    ), 
    status = UFileStatus(
        accessedAt = null, 
        icon = null, 
        metadata = FileMetadataHistory(
            metadata = mapOf("sensitivity" to listOf(FileMetadataDocument(
                createdAt = 1635151675465, 
                createdBy = "user", 
                id = "651233", 
                specification = FileMetadataDocument.Spec(
                    changeLog = "New sensitivity", 
                    document = JsonObject(mapOf("sensitivity" to JsonLiteral(
                        coerceToInlineType = null, 
                        content = "SENSITIVE", 
                        isString = true, 
                    )),)), 
                    templateId = "15123", 
                    version = "1.0.0", 
                ), 
                status = FileMetadataDocument.Status(
                    approval = FileMetadataDocument.ApprovalStatus.Approved(
                        approvedBy = "user", 
                    ), 
                ), 
            ))), 
            templates = mapOf("sensitivity" to FileMetadataTemplate(
                changeLog = "Initial version", 
                createdAt = 0, 
                description = "File sensitivity for files", 
                inheritable = true, 
                namespaceId = "sensitivity", 
                namespaceName = null, 
                namespaceType = FileMetadataTemplateNamespaceType.COLLABORATORS, 
                requireApproval = true, 
                schema = JsonObject(mapOf("type" to JsonLiteral(
                    coerceToInlineType = null, 
                    content = "object", 
                    isString = true, 
                )),"title" to JsonLiteral(
                    coerceToInlineType = null, 
                    content = "UCloud File Sensitivity", 
                    isString = true, 
                )),"required" to listOf(JsonLiteral(
                    coerceToInlineType = null, 
                    content = "sensitivity", 
                    isString = true, 
                ))),"properties" to JsonObject(mapOf("sensitivity" to JsonObject(mapOf("enum" to listOf(JsonLiteral(
                    coerceToInlineType = null, 
                    content = "SENSITIVE", 
                    isString = true, 
                ), JsonLiteral(
                    coerceToInlineType = null, 
                    content = "CONFIDENTIAL", 
                    isString = true, 
                ), JsonLiteral(
                    coerceToInlineType = null, 
                    content = "PRIVATE", 
                    isString = true, 
                ))),"type" to JsonLiteral(
                    coerceToInlineType = null, 
                    content = "string", 
                    isString = true, 
                )),"title" to JsonLiteral(
                    coerceToInlineType = null, 
                    content = "File Sensitivity", 
                    isString = true, 
                )),"enumNames" to listOf(JsonLiteral(
                    coerceToInlineType = null, 
                    content = "Sensitive", 
                    isString = true, 
                ), JsonLiteral(
                    coerceToInlineType = null, 
                    content = "Confidential", 
                    isString = true, 
                ), JsonLiteral(
                    coerceToInlineType = null, 
                    content = "Private", 
                    isString = true, 
                ))),))),))),"dependencies" to JsonObject(mapOf())),)), 
                title = "Sensitivity", 
                uiSchema = JsonObject(mapOf("ui:order" to listOf(JsonLiteral(
                    coerceToInlineType = null, 
                    content = "sensitivity", 
                    isString = true, 
                ))),)), 
                version = "1.0.0", 
            )), 
        ), 
        modifiedAt = null, 
        resolvedProduct = null, 
        resolvedSupport = null, 
        sizeInBytes = null, 
        sizeIncludingChildrenInBytes = null, 
        type = FileType.FILE, 
        unixGroup = null, 
        unixMode = null, 
        unixOwner = null, 
    ), 
    updates = emptyList(), 
    providerGeneratedId = "/51231/my/file", 
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

# In this example, we will show how to create a metadata document and attach it to a file.

# We already have a metadata template in the catalog:

# Authenticated as user
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

# Using this, we can create a metadata document and attach it to our file

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/metadata" -d '{
    "items": [
        {
            "fileId": "/51231/my/file",
            "metadata": {
                "templateId": "15123",
                "version": "1.0.0",
                "document": {
                    "sensitivity": "SENSITIVE"
                },
                "changeLog": "New sensitivity"
            }
        }
    ]
}'


# {
#     "responses": [
#         {
#             "id": "651233"
#         }
#     ]
# }

# This specific template requires approval from a workspace admin. We can do this by calling approve.

curl -XPOST -H "Authorization: Bearer $accessToken" -H "Content-Type: content-type: application/json; charset=utf-8" "$host/api/files/metadata/approve" -d '{
    "items": [
        {
            "id": "651233"
        }
    ]
}'


# {
# }

# We can view the metadata by adding includeMetadata = true when requesting any file

curl -XGET -H "Authorization: Bearer $accessToken" "$host/api/files/retrieve?includeOthers=false&includeUpdates=false&includeSupport=false&includeProduct=false&includeMetadata=true&filterHiddenFiles=false&id=51231" 

# {
#     "id": "/51231/my/file",
#     "specification": {
#         "collection": "51231",
#         "product": {
#             "id": "example-ssd",
#             "category": "example-ssd",
#             "provider": "example"
#         }
#     },
#     "createdAt": 1635151675465,
#     "status": {
#         "type": "FILE",
#         "icon": null,
#         "sizeInBytes": null,
#         "sizeIncludingChildrenInBytes": null,
#         "modifiedAt": null,
#         "accessedAt": null,
#         "unixMode": null,
#         "unixOwner": null,
#         "unixGroup": null,
#         "metadata": {
#             "templates": {
#                 "sensitivity": {
#                     "namespaceId": "sensitivity",
#                     "title": "Sensitivity",
#                     "version": "1.0.0",
#                     "schema": {
#                         "type": "object",
#                         "title": "UCloud File Sensitivity",
#                         "required": [
#                             "sensitivity"
#                         ],
#                         "properties": {
#                             "sensitivity": {
#                                 "enum": [
#                                     "SENSITIVE",
#                                     "CONFIDENTIAL",
#                                     "PRIVATE"
#                                 ],
#                                 "type": "string",
#                                 "title": "File Sensitivity",
#                                 "enumNames": [
#                                     "Sensitive",
#                                     "Confidential",
#                                     "Private"
#                                 ]
#                             }
#                         },
#                         "dependencies": {
#                         }
#                     },
#                     "inheritable": true,
#                     "requireApproval": true,
#                     "description": "File sensitivity for files",
#                     "changeLog": "Initial version",
#                     "namespaceType": "COLLABORATORS",
#                     "uiSchema": {
#                         "ui:order": [
#                             "sensitivity"
#                         ]
#                     },
#                     "namespaceName": null,
#                     "createdAt": 0
#                 }
#             },
#             "metadata": {
#                 "sensitivity": [
#                     {
#                         "type": "metadata",
#                         "id": "651233",
#                         "specification": {
#                             "templateId": "15123",
#                             "version": "1.0.0",
#                             "document": {
#                                 "sensitivity": "SENSITIVE"
#                             },
#                             "changeLog": "New sensitivity"
#                         },
#                         "createdAt": 1635151675465,
#                         "status": {
#                             "approval": {
#                                 "type": "approved",
#                                 "approvedBy": "user"
#                             }
#                         },
#                         "createdBy": "user"
#                     }
#                 ]
#             }
#         },
#         "resolvedSupport": null,
#         "resolvedProduct": null
#     },
#     "owner": {
#         "createdBy": "user",
#         "project": null
#     },
#     "permissions": {
#         "myself": [
#             "ADMIN"
#         ],
#         "others": [
#         ]
#     },
#     "updates": [
#     ]
# }

```


</details>

<details open>
<summary>
<b>Communication Flow:</b> Visual
</summary>

![](/docs/diagrams/files.metadata_sensitivity.png)

</details>



## Remote Procedure Calls

### `browse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses metadata documents_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filemetadatabrowserequest'>FileMetadataBrowseRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#filemetadataattached'>FileMetadataAttached</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Browses in all accessible metadata documents of a user. These are potentially filtered via the flags
provided in the request, such as filtering for specific templates. This endpoint should consider any
`FileCollection` that the user has access to in the currently active project. Note that this endpoint
can only return information about the metadata documents and not the file contents itself. Clients
should generally present the output of this has purely metadata documents, they can link to the real
files if needed. This should eventually result in either a `browse` or `retrieve` call in the files API.


### `retrieveAll`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filemetadataretrieveallrequest'>FileMetadataRetrieveAllRequest</a></code>|<code><a href='#filemetadataretrieveallresponse'>FileMetadataRetrieveAllResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `approve`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `create`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filemetadataaddrequestitem'>FileMetadataAddRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `delete`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filemetadatadeleterequestitem'>FileMetadataDeleteRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `moveMetadata`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filemetadatamoverequestitem'>FileMetadataMoveRequestItem</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `reject`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)



| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `FileMetadataDocument`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A metadata document which conforms to a `FileMetadataTemplate`_

```kotlin
data class FileMetadataDocument(
    val id: String,
    val specification: FileMetadataDocument.Spec,
    val createdAt: Long,
    val status: FileMetadataDocument.Status,
    val createdBy: String,
    val type: String /* "metadata" */,
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
<code>specification</code>: <code><code><a href='#filemetadatadocument.spec'>FileMetadataDocument.Spec</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code>
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#filemetadatadocument.status'>FileMetadataDocument.Status</a></code></code>
</summary>





</details>

<details>
<summary>
<code>createdBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "metadata" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `FileMetadataDocument.Spec`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Specification of a FileMetadataDocument_

```kotlin
data class Spec(
    val templateId: String,
    val version: String,
    val document: JsonObject,
    val changeLog: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>templateId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the `FileMetadataTemplate` that this document conforms to
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The version of the `FileMetadataTemplate` that this document conforms to
</summary>





</details>

<details>
<summary>
<code>document</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code> The document which fills out the template
</summary>





</details>

<details>
<summary>
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Reason for this change
</summary>





</details>



</details>



---

### `FileMetadataDocument.Status`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The current status of a metadata document_

```kotlin
data class Status(
    val approval: FileMetadataDocument.ApprovalStatus,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>approval</code>: <code><code><a href='#filemetadatadocument.approvalstatus'>FileMetadataDocument.ApprovalStatus</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataDocument.ApprovalStatus`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The approval status of a metadata document_

```kotlin
sealed class ApprovalStatus {
    class Approved : ApprovalStatus()
    class NotRequired : ApprovalStatus()
    class Pending : ApprovalStatus()
    class Rejected : ApprovalStatus()
}
```



---

### `FileMetadataDocument.ApprovalStatus.Approved`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The metadata change has been approved by an admin in the workspace_

```kotlin
data class Approved(
    val approvedBy: String,
    val type: String /* "approved" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>approvedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "approved" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `FileMetadataDocument.ApprovalStatus.Pending`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The metadata document has not yet been approved_

```kotlin
data class Pending(
    val type: String /* "pending" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "pending" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `FileMetadataDocument.ApprovalStatus.Rejected`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The metadata document has been rejected by an admin of the workspace_

```kotlin
data class Rejected(
    val rejectedBy: String,
    val type: String /* "rejected" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>rejectedBy</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "rejected" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `FileMetadataDocument.ApprovalStatus.NotRequired`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The metadata document does not require approval_

```kotlin
data class NotRequired(
    val type: String /* "not_required" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>type</code>: <code><code>String /* "not_required" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>



---

### `FileMetadataAttached`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataAttached(
    val path: String,
    val metadata: FileMetadataDocument,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>path</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#filemetadatadocument'>FileMetadataDocument</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataTemplate`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`_

```kotlin
data class FileMetadataTemplate(
    val namespaceId: String,
    val title: String,
    val version: String,
    val schema: JsonObject,
    val inheritable: Boolean,
    val requireApproval: Boolean,
    val description: String,
    val changeLog: String,
    val namespaceType: FileMetadataTemplateNamespaceType,
    val uiSchema: JsonObject?,
    val namespaceName: String?,
    val createdAt: Long?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>namespaceId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The ID of the namespace that this template belongs to
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The title of this template. It does not have to be unique.
</summary>





</details>

<details>
<summary>
<code>version</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Version identifier for this version. It must be unique within a single template group.
</summary>





</details>

<details>
<summary>
<code>schema</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code> JSON-Schema for this document
</summary>





</details>

<details>
<summary>
<code>inheritable</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> Makes this template inheritable by descendants of the file that the template is attached to
</summary>





</details>

<details>
<summary>
<code>requireApproval</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code> If `true` then a user with `ADMINISTRATOR` rights must approve all changes to metadata
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Description of this template. Markdown is supported.
</summary>





</details>

<details>
<summary>
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> A description of the change since last version. Markdown is supported.
</summary>





</details>

<details>
<summary>
<code>namespaceType</code>: <code><code><a href='#filemetadatatemplatenamespacetype'>FileMetadataTemplateNamespaceType</a></code></code>
</summary>





</details>

<details>
<summary>
<code>uiSchema</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>namespaceName</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code>
</summary>





</details>



</details>



---

### `FileMetadataTemplateNamespaceType`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Determines how the metadata template is namespaces_

```kotlin
enum class FileMetadataTemplateNamespaceType {
    COLLABORATORS,
    PER_USER,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>COLLABORATORS</code> The template is namespaced to all collaborators
</summary>



This means at most one metadata document can exist per file.


</details>

<details>
<summary>
<code>PER_USER</code> The template is namespaced to a single user
</summary>



This means that a metadata document might exist for every user who has/had access to the file.


</details>



</details>



---

### `FileMetadataAddRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataAddRequestItem(
    val fileId: String,
    val metadata: FileMetadataDocument.Spec,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>fileId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>metadata</code>: <code><code><a href='#filemetadatadocument.spec'>FileMetadataDocument.Spec</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataBrowseRequest`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_The base type for requesting paginated content._

```kotlin
data class FileMetadataBrowseRequest(
    val filterTemplate: String?,
    val filterVersion: String?,
    val filterActive: Boolean?,
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
<code>filterTemplate</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters on the `templateId` attribute of metadata documents
</summary>





</details>

<details>
<summary>
<code>filterVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> Filters on the `version` attribute of metadata documents.Requires `filterTemplate` to be specified`
</summary>





</details>

<details>
<summary>
<code>filterActive</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Determines if this should only fetch document which have status `not_required` or `approved`
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

### `FileMetadataDeleteRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataDeleteRequestItem(
    val id: String,
    val changeLog: String,
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
<code>changeLog</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataMoveRequestItem`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataMoveRequestItem(
    val oldFileId: String,
    val newFileId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>oldFileId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>newFileId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataRetrieveAllRequest`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataRetrieveAllRequest(
    val fileId: String,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>fileId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>



</details>



---

### `FileMetadataRetrieveAllResponse`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FileMetadataRetrieveAllResponse(
    val metadata: List<FileMetadataAttached>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>metadata</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#filemetadataattached'>FileMetadataAttached</a>&gt;</code></code>
</summary>





</details>



</details>



---

