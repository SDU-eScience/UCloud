# `files.createUpload`

![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Creates an upload session between the user and the provider_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescreateuploadrequestitem'>FilesCreateUploadRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#filescreateuploadresponseitem'>FilesCreateUploadResponseItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The returned endpoint will accept an upload from the user which will create a file at a location
specified in this request.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `404 Not Found` | Either the oldPath or newPath exists or you lack permissions |
| `403 Forbidden` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Uploading a file with the chunked protocol](/docs/reference/files_upload.md) |

