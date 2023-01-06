[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Software Licenses](/docs/developer-guide/orchestration/compute/license.md)

# `LicenseUpdate`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class LicenseUpdate(
    val timestamp: Long?,
    val state: LicenseState?,
    val status: String?,
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
<code>state</code>: <code><code><a href='#licensestate'>LicenseState</a>?</code></code> The new state that the `License` transitioned to (if any)
</summary>





</details>

<details>
<summary>
<code>status</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code> A new status message for the `License` (if any)
</summary>





</details>

<details>
<summary>
<code>binding</code>: <code><code><a href='/docs/reference/dk.sdu.cloud.app.orchestrator.api.JobBinding.md'>JobBinding</a>?</code></code>
</summary>





</details>



</details>


