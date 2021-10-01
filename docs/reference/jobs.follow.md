# `jobs.follow`

![API: Experimental/Beta](https://img.shields.io/static/v1?label=API&message=Experimental/Beta&color=orange&style=flat-square)
![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)


_Follow the progress of a job_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.FindByStringId.md'>FindByStringId</a></code>|<code><a href='#jobsfollowresponse'>JobsFollowResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

Opens a WebSocket subscription to receive updates about a job. These updates include:

- Messages from the provider. For example an update describing state changes or future maintenance.
- State changes from UCloud. For example transition from [`IN_QUEUE`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobState.md) to
  [`RUNNING`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobState.md).
- If supported by the provider, `stdout` and `stderr` from the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md) 


