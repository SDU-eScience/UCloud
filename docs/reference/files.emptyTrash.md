[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.emptyTrash`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Permanently deletes all files from the selected trash folder thereby emptying it_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#findbypath'>FindByPath</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This operation acts as a permanent delete for users. Users will NOT be able to restore the file 
later, if needed. 

Not all providers supports this endpoint. You can query [`files.collections.browse`](/docs/reference/files.collections.browse.md) 
or [`files.collections.retrieve`](/docs/reference/files.collections.retrieve.md)  with the `includeSupport` flag.

This is a long running task. As a result, this operation might respond with a status code which indicate
that it will continue in the background. Progress of this job can be followed using the task API.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `404 Not Found` | Either the oldPath or newPath exists or you lack permissions |
| `403 Forbidden` | You lack permissions to perform this operation |
| `400 Bad Request` | This operation is not supported by the provider |

__Examples:__

| Example |
|---------|
| [Moving files to trash](/docs/reference/files_empty_trash_folder.md) |


