[UCloud Developer Guide](/docs/developer-guide/README.md) / [Orchestration of Resources](/docs/developer-guide/orchestration/README.md) / [Compute](/docs/developer-guide/orchestration/compute/README.md) / [Application Store](/docs/developer-guide/orchestration/compute/appstore/README.md) / [Applications](/docs/developer-guide/orchestration/compute/appstore/apps.md)

# `AppParameterValue.Text`


[![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)](/docs/developer-guide/core/api-conventions.md)


_A textual value_

```kotlin
data class Text(
    val value: String,
    val type: String /* "text" */,
)
```
- __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
- __Mountable as a resource:__ ❌ No
- __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
- __Side effects:__ None

When this is used with an `Enumeration` it must match the value of one of the associated `options`.

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>value</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a></code></code>
</summary>





</details>

<details>
<summary>
<code>type</code>: <code><code>String /* "text" */</code></code> The type discriminator
</summary>

[![API: Stable](https://img.shields.io/static/v1?label=API&message=Stable&color=green&style=flat-square)](/docs/developer-guide/core/api-conventions.md)




</details>



</details>


