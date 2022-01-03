[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# `jobs.provider.PROVIDERID.follow`

[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Follow the progress of a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#jobsproviderfollowrequest'>JobsProviderFollowRequest</a></code>|<code><a href='#jobsproviderfollowresponse'>JobsProviderFollowResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

__Implementation requirements:__ 
 - [`docker.logs = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.Docker.md) or
 - [`virtualMachine.logs = true`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.ComputeSupport.VirtualMachine.md)

For more information, see the end-user API ([`jobs.follow`](/docs/reference/jobs.follow.md))



