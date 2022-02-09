[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.createUpload`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates an upload session between the user and the provider_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescreateuploadrequestitem'>FilesCreateUploadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#filescreateuploadresponseitem'>FilesCreateUploadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The returned endpoint will accept an upload from the user which will create a file at a location
specified in this request.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `HttpStatusCode(value=404, description=Not Found)` | Either the oldPath or newPath exists or you lack permissions |
| `HttpStatusCode(value=403, description=Forbidden)` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Uploading a file with the chunked protocol](/docs/reference/files_upload.md) |


