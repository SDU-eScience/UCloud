[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Public Links (Ingress)](/docs/developer-guide/orchestration/compute/providers/ingress/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/ingress/ingoing.md)

# `ingresses.provider.PROVIDERID.updateAcl`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Callback received by the Provider when permissions are updated_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.provider.api.UpdatedAclWithResource.md'>UpdatedAclWithResource</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Ingress.md'>Ingress</a>&gt;&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This endpoint is mandatory for Providers to implement. If the Provider does not need to keep
internal state, then they may simply ignore this request by responding with `200 OK`. The
Provider _MUST_ reply with an OK status. UCloud/Core will fail the request if the Provider does
not acknowledge the request.


