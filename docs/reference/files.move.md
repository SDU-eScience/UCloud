[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.move`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Move a file from one path to another_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filesmoverequestitem'>FilesMoveRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The file can be of any type. This request is also used for 'renames' of a file. This is simply
considered a move within a single directory. This operation handles conflicts depending on the supplied
`WriteConflictPolicy`.

This is a long running task. As a result, this operation might respond with a status code which indicate
that it will continue in the background. Progress of this job can be followed using the task API.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `400 Bad Request` | The operation couldn't be completed because of the write conflict policy |
| `404 Not Found` | Either the oldPath or newPath exists or you lack permissions |
| `403 Forbidden` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Example of using `move` to rename a file](/docs/reference/files_rename_file.md) |


