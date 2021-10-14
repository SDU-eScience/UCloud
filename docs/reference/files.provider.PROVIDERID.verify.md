[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Storage](/docs/developer-guide/orchestration/storage/README.md) / [Provider APIs](/docs/developer-guide/orchestration/storage/providers/README.md) / [Files](/docs/developer-guide/orchestration/storage/providers/files/README.md) / [Ingoing API](/docs/developer-guide/orchestration/storage/providers/files/ingoing.md)

# `files.provider.PROVIDERID.verify`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Invoked by UCloud/Core to trigger verification of a single batch_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.file.orchestrator.api.UFile.md'>UFile</a>&gt;</code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is periodically invoked by UCloud/Core for resources which are deemed active. The
Provider should immediately determine if these are still valid and recognized by the Provider.
If any of the resources are not valid, then the Provider should notify UCloud/Core by issuing
an update for each affected resource.


