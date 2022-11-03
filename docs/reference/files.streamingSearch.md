[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.streamingSearch`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Searches through the files of a user in all accessible files_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#filesstreamingsearchrequest'>FilesStreamingSearchRequest</a></code>|<code><a href='#filesstreamingsearchresult'>FilesStreamingSearchResult</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint uses a specialized API for returning search results in a streaming fashion. In all other
ways, this endpoint is identical to the normal search API.

This endpoint can be used instead of the normal search API as it will contact providers using the
non-streaming version if they do not support it. In such a case, the core will retrieve multiple pages
in order to stream in more content.

Clients should expect that this endpoint stops returning results after a given timeout. After which,
it is no longer possible to request additional results.


