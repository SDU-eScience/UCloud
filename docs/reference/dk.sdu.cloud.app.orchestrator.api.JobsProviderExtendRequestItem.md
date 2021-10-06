# `JobsProviderExtendRequestItem`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A request to extend the timeAllocation of a Job_

```kotlin
data class JobsProviderExtendRequestItem(
    val job: Job,
    val requestedTime: SimpleDuration,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>job</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a></code></code> The affected Job
</summary>





</details>

<details>
<summary>
<code>requestedTime</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.store.api.SimpleDuration.md'>SimpleDuration</a></code></code> The requested extension, it will be added to the current timeAllocation
</summary>





</details>



</details>

