[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Metadata](/docs/developer-guide/orchestration/storage/metadata/README.md) / [Metadata Documents](/docs/developer-guide/orchestration/storage/metadata/documents.md)

# Example: Sensitivity Document

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

/* Using this, we can create a metadata document and attach it to our file */

FileMetadata.create.call(
    bulkRequestOf(FileMetadataAddRequestItem(
        fileId = "/51231/my/file", 
        metadata = FileMetadataDocument.Spec(
            changeLog = "New sensitivity", 
            document = JsonObject(mapOf("sensitivity" to JsonLiteral(
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
<b>Communication Flow:</b> TypeScript
</summary>

```typescript

/* In this example, we will show how to create a metadata document and attach it to a file. */


/* We already have a metadata template in the catalog: */

// Authenticated as user
await callAPI(FilesMetadataTemplatesApi.retrieveLatest(
    {
        "id": "15123"
    }
);

/*
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
*/

/* Using this, we can create a metadata document and attach it to our file */

await callAPI(FilesMetadataApi.create(
    {
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
    }
);

/*
{
    "responses": [
        {
            "id": "651233"
        }
    ]
}
*/

/* This specific template requires approval from a workspace admin. We can do this by calling approve. */

await callAPI(FilesMetadataApi.approve(
    {
        "items": [
            {
                "id": "651233"
            }
        ]
    }
);

/*
{
}
*/

/* We can view the metadata by adding includeMetadata = true when requesting any file */

await callAPI(FilesApi.retrieve(
    {
        "flags": {
            "includeOthers": false,
            "includeUpdates": false,
            "includeSupport": false,
            "includeProduct": false,
            "includePermissions": null,
            "includeTimestamps": null,
            "includeSizes": null,
            "includeUnixInfo": null,
            "includeMetadata": true,
            "filterCreatedBy": null,
            "filterCreatedAfter": null,
            "filterCreatedBefore": null,
            "filterProvider": null,
            "filterProductId": null,
            "filterProductCategory": null,
            "filterProviderIds": null,
            "filterByFileExtension": null,
            "path": null,
            "allowUnsupportedInclude": null,
            "filterHiddenFiles": false,
            "filterIds": null,
            "hideProductId": null,
            "hideProductCategory": null,
            "hideProvider": null
        },
        "id": "51231"
    }
);

/*
{
    "id": "/51231/my/file",
    "specification": {
        "collection": "51231",
        "product": {
            "id": "example-ssd",
            "category": "example-ssd",
            "provider": "example"
        }
    },
    "createdAt": 1635151675465,
    "status": {
        "type": "FILE",
        "icon": null,
        "sizeInBytes": null,
        "sizeIncludingChildrenInBytes": null,
        "modifiedAt": null,
        "accessedAt": null,
        "unixMode": null,
        "unixOwner": null,
        "unixGroup": null,
        "metadata": {
            "templates": {
                "sensitivity": {
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
            },
            "metadata": {
                "sensitivity": [
                    {
                        "type": "metadata",
                        "id": "651233",
                        "specification": {
                            "templateId": "15123",
                            "version": "1.0.0",
                            "document": {
                                "sensitivity": "SENSITIVE"
                            },
                            "changeLog": "New sensitivity"
                        },
                        "createdAt": 1635151675465,
                        "status": {
                            "approval": {
                                "type": "approved",
                                "approvedBy": "user"
                            }
                        },
                        "createdBy": "user"
                    }
                ]
            }
        },
        "resolvedSupport": null,
        "resolvedProduct": null
    },
    "owner": {
        "createdBy": "user",
        "project": null
    },
    "permissions": {
        "myself": [
            "ADMIN"
        ],
        "others": [
        ]
    },
    "updates": [
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


