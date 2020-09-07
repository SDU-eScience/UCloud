[api](../../index.md) / [dk.sdu.cloud.project.api](../index.md) / [Projects](index.md) / [archive](./archive.md)

# archive

`val archive: CallDescription<`[`ArchiveRequest`](../-archive-request/index.md)`, `[`ArchiveResponse`](../-archive-response.md)`, CommonErrorMessage>`

Archives/unarchives a project.

Archiving a project has no other effect than hiding the project from various calls. No resources should be
deleted as a result of this action.

Archiving can be reversed by calling this endpoint with [ArchiveRequest.archiveStatus](../-archive-request/archive-status.md) `= false`.

