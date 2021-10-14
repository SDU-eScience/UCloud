[UCloud Developer Guide](/docs/developer-guide/README.md) / [Built-in Provider](/docs/developer-guide/built-in-provider/README.md) / [UCloud/Compute](/docs/developer-guide/built-in-provider/compute/README.md) / [Jobs](/docs/developer-guide/built-in-provider/compute/jobs.md)

# `jobs.provider.ucloud.openInteractiveSession`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Opens an interactive session (e.g. terminal, web or VNC)_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderOpenInteractiveSessionRequestItem.md'>JobsProviderOpenInteractiveSessionRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.OpenSession.md'>OpenSession</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

__Implementation requirements:__ 
 - [`docker.vnc = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.Docker.md) or
 - [`docker.terminal = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.Docker.md) or
 - [`docker.web = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.Docker.md) or
 - [`virtualMachine.vnc = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.VirtualMachine.md) or
 - [`virtualMachine.terminal = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.VirtualMachine.md)

For more information, see the end-user API ([`jobs.openInteractiveSession`](/docs/reference/jobs.openInteractiveSession.md))



