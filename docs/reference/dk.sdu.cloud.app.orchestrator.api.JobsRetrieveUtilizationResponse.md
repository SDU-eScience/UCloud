# `JobsRetrieveUtilizationResponse`


![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)



```kotlin
data class JobsRetrieveUtilizationResponse(
    val capacity: CpuAndMemory,
    val usedCapacity: CpuAndMemory,
    val queueStatus: QueueStatus,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>capacity</code>: <code><code><a href='#cpuandmemory'>CpuAndMemory</a></code></code> The total capacity of the entire compute system
</summary>





</details>

<details>
<summary>
<code>usedCapacity</code>: <code><code><a href='#cpuandmemory'>CpuAndMemory</a></code></code> The capacity currently in use, by running jobs, of the entire compute system
</summary>





</details>

<details>
<summary>
<code>queueStatus</code>: <code><code><a href='#queuestatus'>QueueStatus</a></code></code> The system of the queue
</summary>





</details>



</details>

