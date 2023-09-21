[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `JobsFollowResponse`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class JobsFollowResponse(
    val updates: List<JobUpdate>?,
    val log: List<JobsLog>?,
    val newStatus: JobStatus?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>updates</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobupdate'>JobUpdate</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>log</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='#jobslog'>JobsLog</a>&gt;?</code></code>
</summary>





</details>

<details>
<summary>
<code>newStatus</code>: <code><code><a href='#jobstatus'>JobStatus</a>?</code></code>
</summary>





</details>



</details>


