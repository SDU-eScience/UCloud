[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `ComputeSupport.VirtualMachine`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class VirtualMachine(
    val enabled: Boolean?,
    val logs: Boolean?,
    val vnc: Boolean?,
    val terminal: Boolean?,
    val timeExtension: Boolean?,
    val suspension: Boolean?,
    val utilization: Boolean?,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>enabled</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable this feature
</summary>



All other flags are ignored if this is `false`.


</details>

<details>
<summary>
<code>logs</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the log API
</summary>





</details>

<details>
<summary>
<code>vnc</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the VNC API
</summary>





</details>

<details>
<summary>
<code>terminal</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the interactive terminal API
</summary>





</details>

<details>
<summary>
<code>timeExtension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable extension of jobs
</summary>





</details>

<details>
<summary>
<code>suspension</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable suspension of jobs
</summary>





</details>

<details>
<summary>
<code>utilization</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a>?</code></code> Flag to enable/disable the retrieveUtilization of jobs
</summary>





</details>



</details>


