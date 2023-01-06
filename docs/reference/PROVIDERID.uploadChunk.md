[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Upload Protocol](/docs/developer-guide/orchestration/storage/providers/upload.md)

# `PROVIDERID.uploadChunk`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Uploads a new chunk to the file at a given offset_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#chunkeduploadprotocoluploadchunkrequest'>ChunkedUploadProtocolUploadChunkRequest</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Uploads a new chunk to a file, specified by an upload session token. An upload session token can be
created using the [`files.createUpload`](/docs/reference/files.createUpload.md)  call.

A session MUST be live for at least 30 minutes after the last `uploadChunk`
call was active. That is, since the last byte was transferred to this session or processed by the
provider. It is recommended that a provider keep a session for up to 48 hours. A session SHOULD NOT be
kept alive for longer than 48 hours.

This call MUST add the HTTP request body to the file, backed by the session, at the specified offset.
Clients may use the special offset '-1' to indicate that the payload SHOULD be appended to the file.
Providers MUST NOT interpret the request body in any way, the payload is binary and SHOULD be written
to the file as is. Providers SHOULD reject offset values that don't fulfill one of the following
criteria:

- Is equal to -1
- Is a valid offset in the file
- Is equal to the file size + 1

Clients MUST send a chunk which is at most 32MB large (32,000,000 bytes). Clients MUST declare the size
of chunk by specifying the `Content-Length` header. Providers MUST reject values that are not valid or
are too large. Providers SHOULD assume that the `Content-Length` header is valid.
However, the providers MUST NOT wait indefinitely for all bytes to be delivered. A provider SHOULD
terminate a connection which has been idle for too long to avoid trivial DoS by specifying a large
`Content-Length` without sending any bytes.

If a chunk upload is terminated before it is finished then a provider SHOULD NOT delete the data
already written to the file. Clients SHOULD assume that the entire chunk has failed and SHOULD re-upload
the entire chunk.

Providers SHOULD NOT cache a chunk before writing the data to the FS. Data SHOULD be streamed
directly into the file.

Providers MUST NOT respond to this call before the data has been written to disk.

Clients SHOULD avoid sending multiple chunks at the same time. Providers are allowed to reject parallel
calls to this endpoint.


