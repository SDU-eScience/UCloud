[UCloud Developer Guide](/docs/developer-guide/README.md) / [Built-in Provider](/docs/developer-guide/built-in-provider/README.md) / [UCloud/Compute](/docs/developer-guide/built-in-provider/compute/README.md) / [Jobs](/docs/developer-guide/built-in-provider/compute/jobs.md)

# `jobs.provider.ucloud.follow`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Follow the progress of a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowRequest.md'>JobsProviderFollowRequest</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobsProviderFollowResponse.md'>JobsProviderFollowResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

__Implementation requirements:__ 
 - [`docker.logs = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.Docker.md) or
 - [`virtualMachine.logs = true`](/docs/reference/dk.sdu.cloud.app.kubernetes.api.ComputeSupport.VirtualMachine.md)

For more information, see the end-user API ([`jobs.follow`](/docs/reference/jobs.follow.md))



