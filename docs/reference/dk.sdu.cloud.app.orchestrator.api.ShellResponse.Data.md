[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Provider APIs](/docs/developer-guide/orchestration/compute/providers/README.md) / [Shells](/docs/developer-guide/orchestration/compute/providers/shells.md)

# `ShellResponse.Data`


[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_Emitted by the provider when new data is available for the terminal_

```kotlin
data class Data(
    val data: String,
    val type: String /* "data" */,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>data</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "data" */</code></code> The type discriminator
</summary>





</details>



</details>


