[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `jobs.extend`

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Extend the duration of one or more jobs_

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkRequest.md'>BulkRequest</a>&lt;<a href='#jobsextendrequestitem'>JobsExtendRequestItem</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.calls.BulkResponse.md'>BulkResponse</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a>&gt;</code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|

This will extend the duration of one or more jobs in a bulk request. Extension of a job will add to
the current deadline of a job. Note that not all providers support this features. Providers which
do not support it will have it listed in their manifest. If a provider is asked to extend a deadline
when not supported it will send back a 400 bad request.

This call makes no guarantee that all jobs are extended in a single transaction. If the provider
supports it, then all requests made against a single provider should be made in a single transaction.
Clients can determine if their extension request against a specific target was successful by checking
if the time remaining of the job has been updated.

This call will return 2XX if all jobs have successfully been extended. The job will fail with a
status code from the provider one the first extension which fails. UCloud will not attempt to extend
more jobs after the first failure.


