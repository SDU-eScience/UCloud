[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `ApplicationParameter.Peer`


[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_An input parameter which accepts a peering Job_

```kotlin
data class Peer(
    val name: String?,
    val title: String?,
    val description: String,
    val suggestedApplication: String?,
    val defaultValue: Any?,
    val optional: Boolean,
    val type: String /* "peer" */,
)
```
__Compatible with:__ [`AppParameterValue.Peer`](/docs/reference/dk.sdu.cloud.app.store.api.AppParameterValue.Peer.md)

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>name</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>title</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>description</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>suggestedApplication</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>defaultValue</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/'>Any</a>?</code></code>
</summary>





</details>

<details>
<summary>
<code>optional</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/'>Boolean</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "peer" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


