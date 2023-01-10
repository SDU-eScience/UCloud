[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Metadata](/docs/developer-guide/orchestration/storage/metadata/README.md) / [Metadata Templates](/docs/developer-guide/orchestration/storage/metadata/templates.md)

# Example: The Sensitivity Template

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


