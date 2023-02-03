[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/providers/jobs/README.md) / [Ingoing API](/docs/developer-guide/orchestration/compute/providers/jobs/ingoing.md)

# `JobsProviderOpenInteractiveSessionRequestItem`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A request for opening a new interactive session (e.g. terminal)_

```kotlin
data class JobsProviderOpenInteractiveSessionRequestItem(
    val job: Job,
    val rank: Int,
    val sessionType: InteractiveSessionType,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>job</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.Job.md'>Job</a></code></code> The fully resolved Job
</summary>





</details>

<details>
<summary>
<code>rank</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code> The rank of the node (0-indexed)
</summary>



Valid values range from 0 (inclusive) until [`specification.replicas`](#) (exclusive)


</details>

<details>
<summary>
<code>sessionType</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.InteractiveSessionType.md'>InteractiveSessionType</a></code></code> The type of session
</summary>





</details>



</details>


