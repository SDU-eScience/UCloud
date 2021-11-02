[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Metadata](/docs/developer-guide/orchestration/storage/metadata/README.md) / [Metadata Documents](/docs/developer-guide/orchestration/storage/metadata/documents.md)

# `files.metadata.browse`

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


