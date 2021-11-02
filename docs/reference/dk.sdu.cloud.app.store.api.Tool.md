[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md)

# `Tool`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Tools define bundles of software binaries and other assets (e.g. container and virtual machine base-images)._

```kotlin
data class Tool(
    val owner: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NormalizedToolDescription,
)
```
See [Tools](/docs/developer-guide/orchestration/compute/appstore/tools.md) for a more complete discussion.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>owner</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code> The username of the user who created this Tool
</summary>





</details>

<details>
<summary>
<code>createdAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp describing initial creation
</summary>





</details>

<details>
<summary>
<code>modifiedAt</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/'>Long</a></code></code> Timestamp describing most recent modification (Deprecated, Tools are immutable)
</summary>

[![Deprecated: Yes](https://img.shields.io/static/v1?label=Deprecated&message=Yes&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



</details>

<details>
<summary>
<code>description</code>: <code><code><a href='#normalizedtooldescription'>NormalizedToolDescription</a></code></code> The specification for this Tool
</summary>





</details>



</details>


