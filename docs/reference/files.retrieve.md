[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Files](/docs/developer-guide/orchestration/storage/files.md)

# `files.retrieve`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Retrieves information about a single file._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest.md'>ResourceRetrieveRequest</a>&lt;<a href='#ufileincludeflags'>UFileIncludeFlags</a>&gt;</code>|<code><a href='#ufile'>UFile</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This file can be of any type. Clients can request additional information about the file using the
`include*` flags of the request. Note that not all providers support all information. Clients can query
this information using [`files.collections.browse`](/docs/reference/files.collections.browse.md)  or 
[`files.collections.retrieve`](/docs/reference/files.collections.retrieve.md)  with the `includeSupport` flag.


