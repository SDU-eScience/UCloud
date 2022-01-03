[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `Job`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A `Job` in UCloud is the core abstraction used to describe a unit of computation._

```kotlin
data class Job(
    val id: String,
    val owner: ResourceOwner,
    val updates: List<JobUpdate>,
    val specification: JobSpecification,
    val status: JobStatus,
    val createdAt: Long,
    val output: JobOutput?,
    val permissions: ResourcePermissions?,
    val providerGeneratedId: String?,
)
```
They provide users a way to run their computations through a workflow similar to their own workstations but scaling to
much bigger and more machines. In a simplified view, a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  describes the following information:

- The `Application` which the provider should/is/has run (see [app-store](/docs/developer-guide/orchestration/compute/appstore/apps.md))
- The [input parameters](/docs/reference/dk.sdu.cloud.app.store.api.ApplicationParameter.md) required by a `Job`
- A reference to the appropriate [compute infrastructure](/docs/reference/dk.sdu.cloud.accounting.api.Product.md), this
  includes a reference to the _provider_

A `Job` is started by a user request containing the `specification` of a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job..md)  This information is verified by the UCloud
orchestrator and passed to the provider referenced by the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  itself. Assuming that the provider accepts this
information, the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is placed in its initial state, `IN_QUEUE`. You can read more about the requirements of the
compute environment and how to launch the software
correctly [here](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md).

At this point, the provider has acted on this information by placing the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  in its own equivalent of
a job queue. Once the provider realizes that the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is running, it will contact UCloud and place the 
[`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  in the `RUNNING` state. This indicates to UCloud that log files can be retrieved and that interactive
interfaces (`VNC`/`WEB`) are available.

Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  has
terminated successfully if no internal error occurred in UCloud and in the provider. This means that a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  whose
software returns with a non-zero exit code is still considered successful. A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  might, for example, be placed
in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
state. Any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  which is in a terminal state can no longer receive any updates or change its state.

At any point after the user submits the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), they may request cancellation of the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job..md)  This will
stop the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md), delete any
[ephemeral resources](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md#ephemeral-resources) and release
any [bound resources](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md#resources).

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>id</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> Unique identifier for this job.
</summary>



UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier.


</details>

<details>
<summary>
<code>owner</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourceOwner.md'>ResourceOwner</a></code></code> A reference to the owner of this job
</summary>





</details>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobupdate'>JobUpdate</a>&gt;</code></code> A list of status updates from the compute backend.
</summary>



The status updates tell a story of what happened with the job. This list is ordered by the timestamp in ascending order. The current state of the job will always be the last element. `updates` is guaranteed to always contain at least one element.


</details>

<details>
<summary>
<code>specification</code>: <code><code><a href='#jobspecification'>JobSpecification</a></code></code> The specification used to launch this job.
</summary>



This property is always available but must be explicitly requested.


</details>

<details>
<summary>
<code>status</code>: <code><code><a href='#jobstatus'>JobStatus</a></code></code> A summary of the `Job`'s current status
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp referencing when the request for creation was received by UCloud
</summary>





</details>

<details>
<summary>
<code>output</code>: <code><code><a href='#joboutput'>JobOutput</a>?</code></code> Information regarding the output of this job.
</summary>





</details>

<details>
<summary>
<code>permissions</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.provider.api.ResourcePermissions.md'>ResourcePermissions</a>?</code></code> Permissions assigned to this resource
</summary>



A null value indicates that permissions are not supported by this resource type.


</details>

<details>
<summary>
<code>providerGeneratedId</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


