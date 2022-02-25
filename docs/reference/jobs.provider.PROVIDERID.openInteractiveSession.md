[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# `jobs.provider.PROVIDERID.openInteractiveSession`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Opens an interactive session (e.g. terminal, web or VNC)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobsprovideropeninteractivesessionrequestitem'>JobsProviderOpenInteractiveSessionRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.OpenSession.md'>OpenSession</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

__Implementation requirements:__ 
 - [`docker.vnc = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.Docker.md) or
 - [`docker.terminal = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.Docker.md) or
 - [`docker.web = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.Docker.md) or
 - [`virtualMachine.vnc = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.VirtualMachine.md) or
 - [`virtualMachine.terminal = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.VirtualMachine.md)

For more information, see the end-user API ([`jobs.openInteractiveSession`](/docs/reference/jobs.openInteractiveSession.md))


