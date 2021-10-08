[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / [Authentication](/docs/developer-guide/core/users/authentication/README.md) / [Provider Authentication](/docs/developer-guide/core/users/authentication/providers.md)

# `auth.providers.refreshAsOrchestrator`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Signs an access-token to be used by a UCloud service_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#authprovidersrefreshasproviderrequestitem'>AuthProvidersRefreshAsProviderRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.auth.api.AccessToken.md'>AccessToken</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This RPC signs an access-token which will be used by authorized UCloud services to act as an
orchestrator of resources.


