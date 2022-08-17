[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Jobs](/docs/developer-guide/orchestration/compute/jobs.md)

# `ExportedParameters`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class ExportedParameters(
    val siteVersion: Int,
    val request: ExportedParametersRequest,
    val resolvedResources: ExportedParameters.Resources?,
    val machineType: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>siteVersion</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/'>Int</a></code></code>
</summary>





</details>

<details>
<summary>
<code>request</code>: <code><code><a href='#exportedparametersrequest'>ExportedParametersRequest</a></code></code>
</summary>





</details>

<details>
<summary>
<code>resolvedResources</code>: <code><code><a href='#exportedparameters.resources'>ExportedParameters.Resources</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>machineType</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>


