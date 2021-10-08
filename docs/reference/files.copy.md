[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.copy`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Copies a file from one path to another_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#filescopyrequestitem'>FilesCopyRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='#longrunningtask'>LongRunningTask</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The file can be of any type. If a directory is chosen then this will recursively copy all of its
children. This request might fail half-way through. This can potentially lead to a situation where
a partial file is left on the file-system. It is left to the user to clean up this file.

This operation handles conflicts depending on the supplied `WriteConflictPolicy`.

This is a long running task. As a result, this operation might respond with a status code which
indicate that it will continue in the background. Progress of this job can be followed using the
task API.

UCloud applied metadata will not be copied to the new file. File-system metadata (e.g.
extended-attributes) may be moved, however this is provider dependant.

__Errors:__

| Status Code | Description |
|-------------|-------------|
| `400 Bad Request` | The operation couldn't be completed because of the write conflict policy |
| `404 Not Found` | Either the oldPath or newPath exists or you lack permissions |
| `403 Forbidden` | You lack permissions to perform this operation |

__Examples:__

| Example |
|---------|
| [Example of duplicating a file](/docs/reference/files_copy_file_to_self.md) |


