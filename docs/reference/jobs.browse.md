[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `jobs.browse`

[![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Browses the catalog of all Jobs_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest.md'>ResourceBrowseRequest</a>&lt;<a href='#jobincludeflags'>JobIncludeFlags</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.PageV2.md'>PageV2</a>&lt;<a href='#job'>Job</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

The catalog of all [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s works through the normal pagination and the return value can be
adjusted through the [flags](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobIncludeFlags.md). This can include filtering by a specific
application or looking at [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)s of a specific state, such as
(`RUNNING`)[/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobState.md).


