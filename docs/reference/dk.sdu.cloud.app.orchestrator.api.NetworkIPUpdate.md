[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Public IPs (NetworkIP)](/docs/developer-guide/orchestration/compute/ips.md)

# `NetworkIPUpdate`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class NetworkIPUpdate(
    val timestamp: Long?,
    val state: NetworkIPState?,
    val status: String?,
    val changeIpAddress: Boolean?,
    val newIpAddress: String?,
    val binding: JobBinding?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>timestamp</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a>?</code></code> A timestamp for when this update was registered by UCloud
</summary>





</details>

<details>
<summary>
<code>state</code>: <code><code><a href='#networkipstate'>NetworkIPState</a>?</code></code> The new state that the `NetworkIP` transitioned to (if any)
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A new status message for the `NetworkIP` (if any)
</summary>





</details>

<details>
<summary>
<code>changeIpAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>newIpAddress</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>binding</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobBinding.md'>JobBinding</a>?</code></code>
</summary>





</details>



</details>


