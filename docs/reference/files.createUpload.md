[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.createUpload`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Creates an upload session between the user and the provider_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescreateuploadrequestitem'>FilesCreateUploadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#filescreateuploadresponseitem'>FilesCreateUploadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

An upload can be either a file or folder, if supported by the provider, and depending on the
[`UploadTypespecified`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UploadTypespecified.md)  in the request. The desired path and a list of supported [`UploadProtocol`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UploadProtocol.md)s 
are also specified in the request. The latter is used by the provider to negotiate which protocol to use.

The response will contain an endpoint which is ready to accept the upload, as well as the chosen
[`UploadProtocol`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UploadProtocol.md)  and a unique token.

At the time of writing the default and preferred protocol is [`UploadProtocol.WEBSOCKET`](/docs/reference/dk.sdu.cloud.file.orchestrator.api.UploadProtocol.WEBSOCKET.md).

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `404 Not Found` | Either the oldPath or newPath exists or you lack permissions |
| `403 Forbidden` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Uploading a file with the chunked protocol](/docs/reference/files_upload.md) |


