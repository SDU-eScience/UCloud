[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `JobState`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A value describing the current state of a Job_

```kotlin
enum class JobState {
    IN_QUEUE,
    RUNNING,
    CANCELING,
    SUCCESS,
    FAILURE,
    EXPIRED,
    SUSPENDED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>IN_QUEUE</code> Any Job which is not yet ready
</summary>



More specifically, this state should apply to any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  for which all of the following holds:

- The [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  has been created
- It has never been in a final state
- The number of `replicas` which are running is less than the requested amount


</details>

<details>
<summary>
<code>RUNNING</code> A Job where all the tasks are running
</summary>



More specifically, this state should apply to any [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  for which all of the following holds:

- All `replicas` of the [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  have been started

---

__📝 NOTE:__ A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  can be `RUNNING` without actually being ready. For example, if a [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  
exposes a web interface, then the web-interface doesn't have to be available yet. That is, the server might
still be running its initialization code.

---


</details>

<details>
<summary>
<code>CANCELING</code> A Job which has been cancelled but has not yet terminated
</summary>



---

__📝 NOTE:__ This is only a temporary state. The [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  is expected to eventually transition to a final
state, typically the `SUCCESS` state.

---


</details>

<details>
<summary>
<code>SUCCESS</code> A Job which has terminated without a _scheduler_ error
</summary>



---

__📝 NOTE:__ A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  will complete successfully even if the user application exits with an unsuccessful 
status code.

---


</details>

<details>
<summary>
<code>FAILURE</code> A Job which has terminated with a failure
</summary>



---

__📝 NOTE:__ A [`Job`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md)  should _only_ fail if it is the scheduler's fault

---


</details>

<details>
<summary>
<code>EXPIRED</code> A Job which has expired and was terminated as a result
</summary>



This state should only be used if the [`timeAllocation`](/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobSpecification.md) has expired. Any other
form of cancellation/termination should result in either `SUCCESS` or `FAILURE`.


</details>

<details>
<summary>
<code>SUSPENDED</code> A Job which might have previously run but is no longer running, this state is not final.
</summary>



Unlike SUCCESS and FAILURE a Job can transition from this state to one of the active states again.


</details>



</details>


